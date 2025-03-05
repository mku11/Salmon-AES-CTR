# salmon makefile
SALMON_VERSION=2.3.0
SRC_ROOT=../../src/c
SALMON_ROOT=$(SRC_ROOT)/salmon
SALMON_JNI_ROOT=$(SRC_ROOT)/salmon-jni
USE_AES_GPU?=0
OPENCL_ROOT?=
OUTPUT_ROOT=../../../output
CL_TARGET_OPENCL_VERSION?=120
OPENCL_KERNEL=salmon-aes-kernel.c
       
SALMON_INCLUDE=$(SALMON_ROOT)/include
SALMON_JNI_INCLUDE=$(SALMON_JNI_ROOT)/include

OPENCL_INCLUDE=$(OPENCL_ROOT)/include
OPENCL_LIB=$(OPENCL_ROOT)/lib

$(info Usage:)
$(info make PLATFORM=<value> ARCH=<value> ENABLE_JNI=<value>  USE_AES_GPU=<value> OPENCL_ROOT=<path>)
$(info PLATFORM is the target OS, value: win, linux, macos)
$(info ARCH is the target architecture, value: x86_64, aarch64)
$(info ENABLE_JNI to use this lib from Java, value: 0 to disable, 1 to enable)
$(info USE_AES_GPU to include GPU accel, value: 0 to disable, 1 to enable)
$(info Example: make PLATFORM=win ARCH=x86_64 ENABLE_JNI=1 USE_AES_GPU=1)
$(info )

PLATFORM?=win
ARCH?=x86_64
CC?=gcc

ifeq ($(ARCH),aarch64)
  CC=aarch64-$(PLATFORM)-gnu-gcc
  CFLAGS+=-march=armv8-a+crypto
else
  ARCH=x86_64
  CC=gcc
  CFLAGS+=-maes
endif

PACKAGE_NAME=salmon-gcc-$(PLATFORM)-$(ARCH).$(SALMON_VERSION)
PACKAGE_FILE=$(PACKAGE_NAME).tar.gz
OUTPUT_DIR=$(OUTPUT_ROOT)/native
ifeq ($(PLATFORM),win)
  LIB_NAME=salmon.dll
else ifeq ($(PLATFORM),linux)
  LIB_NAME=libsalmon.so
else ifeq ($(PLATFORM),macos)
  LIB_NAME=libsalmon.dylib
endif

CFLAGS+=-I$(SALMON_INCLUDE)
CFLAGS+=-fPIC
LIBS=-lm

ifeq ($(ENABLE_JNI),1)
  CFLAGS+=-I$(SALMON_JNI_INCLUDE) -I"$(JAVA_HOME)/include"
  ifeq ($(PLATFORM),win)
	CFLAGS+=-I"$(JAVA_HOME)/include/win32"
    CFLAGS+=-D__int64=long
  endif
  ifeq ($(PLATFORM),linux)
	CFLAGS+=-I"$(JAVA_HOME)/include/linux"
  endif
  ifeq ($(PLATFORM),macos)
	CFLAGS+=-I"$(JAVA_HOME)/include/darwin"
  endif
endif

OBJ_DIR=obj
MAIN_LIB_DIR=lib
LIB_DIR =$(MAIN_LIB_DIR)/$(ARCH)

SRC=$(wildcard ../../src/c/salmon/src/*.c)

ifeq ($(ENABLE_JNI),1)
  _SRC+=../../src/c/salmon-jni/src/salmon-jni.c
endif

ifeq ($(USE_AES_GPU),1)
	SRC+=$(OPENCL_KERNEL)
	CFLAGS+=-I$(OPENCL_INCLUDE)
	CFLAGS+=-DUSE_OPENCL=1 -DCL_TARGET_OPENCL_VERSION=$(CL_TARGET_OPENCL_VERSION)
	CFLAGS+=-L$(OPENCL_LIB)
	LIBS+=-lOpenCL
endif

_SALMON_DEPS = salmon.h salmon-aes-intr.h salmon-aes-opencl.h
SALMON_DEPS = $(patsubst %,$(SALMON_INCLUDE)/%,$(_SALMON_DEPS))

OBJECT := $(SRC:.c=.o)

.PHONY: all	
all: $(LIB_NAME)

%.o: %.c $(SALMON_DEPS)
	@mkdir -p $(subst $(SRC_ROOT),$(OBJ_DIR),$(@D))
	$(CC) -c -o $(subst $(SRC_ROOT),$(OBJ_DIR),$@) $< $(CFLAGS)

$(LIB_NAME): $(OBJECT)
	@mkdir -p $(LIB_DIR)
	$(CC) -shared -o $(LIB_DIR)/$@ $(subst $(SRC_ROOT),$(OBJ_DIR),$^) $(CFLAGS) $(LIBS)

package: $(LIB_NAME)
	@mkdir -p $(OUTPUT_DIR)/$(PACKAGE_NAME)
	rm -rf $(OUTPUT_DIR)/$(PACKAGE_NAME)/*
	@mkdir -p $(OUTPUT_DIR)/$(PACKAGE_NAME)/include
	cp -r $(SALMON_INCLUDE)/* $(OUTPUT_DIR)/$(PACKAGE_NAME)/include
	@mkdir -p $(OUTPUT_DIR)/$(PACKAGE_NAME)/lib
	cp $(LIB_DIR)/$(LIB_NAME) $(OUTPUT_DIR)/$(PACKAGE_NAME)/lib
	cp README.txt $(OUTPUT_DIR)/$(PACKAGE_NAME)
	cd $(OUTPUT_DIR) && tar -czvf $(PACKAGE_FILE) $(PACKAGE_NAME)/
	
.PHONY: all

clean:
	rm -rf $(OBJ_DIR)/* $(MAIN_LIB_DIR)/* $(PACKAGES_DIR)/*