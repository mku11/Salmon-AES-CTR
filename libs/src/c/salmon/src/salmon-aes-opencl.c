/*
MIT License

Copyright (c) 2024 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <stdbool.h>
#include <CL/opencl.h>
#include "salmon-aes-opencl.h"

#define MAX_PLATFORMS 8
#define MAX_DEVICES 8
#define MAX_CHARS 1024
#define MAX_SOURCE_SIZE 8*1024

#define SALMON_AES_FILE "salmon-aes.c"
#define KERNEL_FILE "salmon-aes-kernel.cl"
#define KERNEL_NAME "kernel_aes_transform_ctr"

#define BLOCKS_PER_WORKITEM 4
#define DISABLE_OPT 0

bool init = false;
int platform_index = 0;
int currentDevice = 0;
int num_platforms;
int num_devices;
char* source_str = NULL;
size_t kernel_max_local_size;
char device_name[MAX_CHARS];
size_t max_shared_mem;
cl_platform_id platforms[MAX_PLATFORMS];
cl_platform_id cpPlatform;
cl_device_id device_ids[MAX_DEVICES];
cl_device_id device_id = NULL;
cl_context context;           
cl_command_queue queue;       
cl_program program;
cl_kernel kernel;

void aes_opencl_key_expand(const unsigned char* userkey, unsigned char* key) {
	aes_key_expand(key, userkey);
}

void print_build_error(cl_device_id device_id, cl_program program) {
	size_t log_size;
	clGetProgramBuildInfo(program, device_id, CL_PROGRAM_BUILD_LOG, 0, NULL, &log_size);
	char* log = (char*)malloc(log_size);
	clGetProgramBuildInfo(program, device_id, CL_PROGRAM_BUILD_LOG, log_size, log, NULL);
	printf("%s\n", log);
	free(log);
}

int init_opencl() {
	size_t strSize = (sizeof(char) * MAX_CHARS);
	size_t retSize;

	FILE* cl_code = NULL;
	FILE* cl_code1 = NULL;
	
	if (device_id)
		return 0;

	cl_int err = clGetPlatformIDs(MAX_PLATFORMS, platforms, &num_platforms);
	err = clGetPlatformIDs(MAX_PLATFORMS, platforms, &num_platforms);

	// choose the platform
	cpPlatform = platforms[platform_index];
	// printf("using platform: %d\n", platform_index);

	// Get IDs for the device
	err = clGetDeviceIDs(cpPlatform, CL_DEVICE_TYPE_GPU, MAX_DEVICES, device_ids, &num_devices);

	device_id = device_ids[currentDevice];
	err = clGetDeviceInfo(device_id, CL_DEVICE_NAME, strSize, (void*) device_name, &retSize);
	if (err != CL_SUCCESS) {
		printf("Could not get device name, code: %d\n", err);
		goto error;
	}
	// printf("using device: %d:%s\n", currentDevice, device_name);

	// Create a context 
	context = clCreateContext(0, 1, &device_id, NULL, NULL, &err);
	if (err != CL_SUCCESS) {
		printf("Could not create context, code: %d\n", err);
		goto error;
	}

	// Create a command queue
	queue = clCreateCommandQueue(context, device_id, CL_QUEUE_PROFILING_ENABLE, &err);
	if (err != CL_SUCCESS) {
		printf("Could not create command queue, code: %d\n", err);
		goto error;
	}

	err = clGetDeviceInfo(device_id, CL_DEVICE_LOCAL_MEM_SIZE, sizeof(max_shared_mem), &max_shared_mem, 0);
	// printf("max_shared_mem: %d\n", (int) max_shared_mem);

	source_str = (char*)malloc(2*MAX_SOURCE_SIZE + 1);
	char* ptr = source_str;
	cl_code = fopen(SALMON_AES_FILE, "rb");
	if (cl_code == NULL) {
		printf("Could not open kernel file: %s\n", SALMON_AES_FILE);
		goto error;
	}
	ptr += fread(ptr, 1, MAX_SOURCE_SIZE, cl_code);
	strcpy(ptr, "\n\n");
	ptr+=2;
	char defines[1024];
	sprintf(defines, "#define CHUNK_SIZE %d\n\n", 16*BLOCKS_PER_WORKITEM);
	strcpy(ptr, defines);
	ptr += strlen(defines);

	cl_code1 = fopen(KERNEL_FILE, "rb");
	if (cl_code1 == NULL) {
		printf("Could not open kernel file: %s\n", KERNEL_FILE);
		goto error;
	}
	ptr += fread(ptr, 1, MAX_SOURCE_SIZE, cl_code1);
	strcpy(ptr, "\n\n");

	// printf("kernel source: %s\n", source_str);

	// Create the compute program from the source buffer
	program = clCreateProgramWithSource(context, 1, (const char**)&source_str, NULL, &err);
	if (err != CL_SUCCESS) {
		printf("Could not create program, code: %d\n", err);
		goto error;
	}

	// Build the program executable
	err = clBuildProgram(program, 0, NULL, DISABLE_OPT?"-cl-opt-disable":NULL, NULL, NULL);
	if (err != CL_SUCCESS) {
		printf("Could not build program, code: %d\n", err);
		if (err == CL_BUILD_PROGRAM_FAILURE) {
			print_build_error(device_id, program);
		}
		goto error;
	}

	// Create the compute kernel in the program we wish to run
	kernel = clCreateKernel(program, KERNEL_NAME, &err);
	if (err != CL_SUCCESS) {
		printf("Could not create kernel: %s, code: %d\n", KERNEL_NAME, err);
		goto error;
	}

	err = clGetKernelWorkGroupInfo(kernel, device_id, CL_KERNEL_WORK_GROUP_SIZE,
		sizeof(kernel_max_local_size), &kernel_max_local_size, NULL);
	// printf("kernel_max_local_size: %d\n", (int) kernel_max_local_size);

error:
	if(cl_code)
		fclose(cl_code);
	if (cl_code1)
		fclose(cl_code1);

	return err;
}

void destroy_opencl() {

	cl_int err = clReleaseKernel(kernel);
	if (err != CL_SUCCESS) {
		printf("Could not release kernel, code: %d\n", err);
		exit(1);
	}

	err = clReleaseProgram(program);
	if (err != CL_SUCCESS) {
		printf("Could not release program, code: %d\n", err);
		exit(1);
	}
	free(source_str);
}

static int transform_opencl(const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count)
{
	cl_mem d_key;
	cl_mem d_ctr;
	cl_mem d_src;
	cl_mem d_dest;
	
	cl_int err;
	size_t local[1], global[1];
	int chunks = (int) ceil(count / (double) (16* BLOCKS_PER_WORKITEM));
	local[0] = kernel_max_local_size;
	global[0] = ((int) ceil(chunks / (double) local[0])) * local[0];

	d_key = clCreateBuffer(context, CL_MEM_READ_ONLY, 240 * sizeof(*key), NULL, NULL);
	d_ctr = clCreateBuffer(context, CL_MEM_READ_ONLY, 16 * sizeof(*counter), NULL, NULL);
	d_src = clCreateBuffer(context, CL_MEM_READ_ONLY, count * sizeof(*srcBuffer), NULL, NULL);
	d_dest = clCreateBuffer(context, CL_MEM_WRITE_ONLY, count * sizeof(*destBuffer), NULL, NULL);

	// Write our data set into the input array in device memory
	err = clEnqueueWriteBuffer(queue, d_key, CL_TRUE, 0, 240 * sizeof(*key), key, 0, NULL, NULL);
	err = clEnqueueWriteBuffer(queue, d_ctr, CL_TRUE, 0, 16 * sizeof(*counter), counter, 0, NULL, NULL);
	err = clEnqueueWriteBuffer(queue, d_src, CL_TRUE, 0, count * sizeof(*srcBuffer), srcBuffer, 0, NULL, NULL);
	if (err != CL_SUCCESS) {
		printf("Could not enqueue buffers, code: %d\n", err);
		exit(1);
	}

	// Set the arguments to our compute kernel
	int param = 0;
	err = clSetKernelArg(kernel, param++, sizeof(cl_mem), (void*)&d_key);
	err |= clSetKernelArg(kernel, param++, sizeof(cl_mem), (void*)&d_ctr);
	err |= clSetKernelArg(kernel, param++, sizeof(cl_mem), (void*)&d_src);
	err |= clSetKernelArg(kernel, param++, sizeof(int), (void*)&srcOffset);
	err |= clSetKernelArg(kernel, param++, sizeof(cl_mem), (void*)&d_dest);
	err |= clSetKernelArg(kernel, param++, sizeof(int), (void*)&destOffset);
	err |= clSetKernelArg(kernel, param++, sizeof(int), (void*)&count);
	if (err != CL_SUCCESS) {
		printf("Could not set kernel args, code: %d\n", err);
		exit(1);
	}
	
	// printf("local_size: %d, global_size: %d\r\n", (int) local[0], (int) global[0]);

	cl_event event;
	cl_ulong time_start = 0;
	cl_ulong time_end = 0;
	double time_passed_kernel;

	// printf("exec kernel: %s\r\n", kernel_name);
	fflush(stdout);
	// Execute the kernel over the entire range of the data set 
	err = clEnqueueNDRangeKernel(queue, kernel, 1, NULL, global, local, 0, NULL, &event);
	if (err != CL_SUCCESS) {
		printf("Could not exec kernel, code: %d\n", err);
		exit(1);
	}

	clWaitForEvents(1, &event);
	clFinish(queue);
	err = clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_START, sizeof(time_start), &time_start, NULL);
	err |= clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_END, sizeof(time_end), &time_end, NULL);
	if (err != CL_SUCCESS) {
		printf("Could not get profiling kernel, code: %d\n", err);
		exit(1);
	}

	time_passed_kernel = (time_end - time_start) / (double)1e9;
	// printf("kernel time (sec): %f\n", time_passed_kernel);

	// Wait for the command queue to get serviced before reading back results
	clFinish(queue);
	// Read the results from the device
	clEnqueueReadBuffer(queue, d_dest, CL_TRUE, destOffset, count * sizeof(*destBuffer), destBuffer, 0, NULL, &event);
	clWaitForEvents(1, &event);
	clFinish(queue);

	err = clReleaseEvent(event);
	if (err != CL_SUCCESS) {
		printf("Could not release event, code: %d\n", err);
		exit(1);
	}

	err = clReleaseMemObject(d_key);
	err |= clReleaseMemObject(d_ctr);
	err |= clReleaseMemObject(d_src);
	err |= clReleaseMemObject(d_dest);
	if (err != CL_SUCCESS) {
		printf("Could not release resources, code: %d\n", err);
		exit(1);
	}

	fflush(stdout);
	return 0;
}

int aes_opencl_transform(const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {

	return transform_opencl(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
}

int aes_opencl_transform_ctr(const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {
	return aes_opencl_transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
}