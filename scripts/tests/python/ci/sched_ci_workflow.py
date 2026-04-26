from tasktiger import TaskTiger
import tasks

tiger = TaskTiger()

lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2 = None, None, None, None
lvl1_aesGpu1, lvl1_aesGpu2 = None, None
lvl2_default1, lvl2_default2 = None, None
group0, group1, group2, group3 = None, None, None, None
gpu = False
timeout = 30 * 60

def sched_lvl1():
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl1_aesGpu1, lvl1_aesGpu2

    # LVL1 native
    cls = "salmon_native_tests.SalmonNativeTests"
    path = "../../../../libs/test/salmon_native_test_python"

    group1 = []
    group1.append(
        lvl1_aes1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "1"},
            ),
        )
    )
    group1.append(
        lvl1_aes2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "2"},
            ),
        )
    )

    # lvl1 intrinsics
    group1.append(
        lvl1_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "AesIntrinsics",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                },
            ),
        )
    )
    group1.append(
        lvl1_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "AesIntrinsics",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                },
            ),
        )
    )

    if gpu:
        # lvl1 gpu - opencl not working on vm
        group1.append(
            lvl1_aesGPU1 := tiger.delay(
                tasks.run_test,
                args=(
                    cls,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "true",
                        "ENC_THREADS": "1",
                    },
                ),
            )
        )
        group1.append(
            lvl1_aesGPU2 := tiger.delay(
                tasks.run_test,
                args=(
                    cls,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "true",
                        "ENC_THREADS": "2",
                    },
                ),
            )
        )


def sched_lvl2():
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl2_default1, lvl2_default2

    cls = "salmon_core_tests.SalmonCoreTests"
    path = "../../../../libs/test/salmon_core_test_python"
    # # LVL 2 core
    group2 = []
    # lvl2 core default
    group2.append(
        lvl2_default1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                },
            ),
        )
    )
    group2.append(
        lvl2_default2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                },
            ),
        )
    )
    # lvl2 core aes native
    group2.append(
        lvl2_aes1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Aes",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl1_aes1])),
        )
    )
    group2.append(
        lvl2_aes2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Aes",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl1_aes1, lvl1_aes2])),
        )
    )

    # lvl2 core intrinsics
    group2.append(
        lvl2_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "AesIntrinsics",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl1_aesIntr1])),
        )
    )
    group2.append(
        lvl2_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "AesIntrinsics",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl1_aesIntr1, lvl1_aesIntr2])),
        )
    )

    # lvl2 core gpu - not working on vm
    if gpu:
        group2.append(
            lvl2_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=(
                    cls,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "enable",
                        "ENC_THREADS": "1",
                    },
                ),
                depends=list(map(lambda x: x.id, [lvl1_aesGpu1])),
            )
        )
        group2.append(
            lvl2_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=(
                    cls,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "enable",
                        "ENC_THREADS": "2",
                    },
                ),
                depends=list(map(lambda x: x.id, [lvl1_aesGpu1, lvl1_aesGpu2])),
            )
        )


def sched_lvl3():
    global lvl2_default1, lvl2_default2
    cls = "salmon_fs_tests.SalmonFSTests"
    clsHttp = "salmon_fs_http_tests.SalmonFSHttpTests"
    path = "../../../../libs/test/salmon_fs_test_python"

    # # LVL 3 fs
    group3 = []
    # lvl2 fs local
    group3.append(
        lvl3_fsDefault1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                    "TEST_MODE": "Local",
                    "TEST_DIR": "/tmp/salmon/test",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl2_default1])),
        )
    )
    group3.append(
        lvl3_fsDefault2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                    "TEST_MODE": "Local",
                    "TEST_DIR": "/tmp/salmon/test",
                },
            ),
            depends=list(map(lambda x: x.id, [lvl2_default1, lvl2_default2])),
        )
    )
    # lvl3 fs http
    group3.append(
        lvl3_httpDefault1 := tiger.delay(
            tasks.run_test,
            args=(
                clsHttp,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                    "TEST_MODE": "Http",
                    "TEST_DIR": "/var/www/salmon/test",
                    "HTTP_SERVER_URL": "http://localhost",
                },
            ),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, [lvl2_default1])),
        )
    )
    group3.append(
        lvl3_httpDefault2 := tiger.delay(
            tasks.run_test,
            args=(
                clsHttp,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                    "TEST_MODE": "Http",
                    "TEST_DIR": "/var/www/salmon/test",
                    "HTTP_SERVER_URL": "http://localhost",
                },
            ),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, [lvl2_default1, lvl2_default2])),
        )
    )
    group3.append(
        lvl3_wsDefault1 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                    "TEST_MODE": "WebService",
                    "TEST_DIR": "/tmp/salmon/test",
                    "WS_SERVER_URL": "http://localhost:8080",
                },
            ),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, [lvl2_default1])),
        )
    )
    group3.append(
        lvl3_wsDefault2 := tiger.delay(
            tasks.run_test,
            args=(
                cls,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "2",
                    "TEST_MODE": "WebService",
                    "TEST_DIR": "/tmp/salmon/test",
                    "WS_SERVER_URL": "http://localhost:8080",
                },
            ),
            hard_timeout=timeout,
            depends=list(
                map(lambda x: x.id, [lvl2_default1, lvl2_default2])
            ),
        )
    )


def sched_lvl4():
    global group1, group2, group3

    # # LVL 4 perf - no concurrency
    group4 = []
    group4.append(
        lvl4_perf := tiger.delay(
            tasks.run_test,
            args=(
                "salmon_core_perf_tests.SalmonCorePerfTests",
                "../../../../libs/test/salmon_core_test_python",
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": "false",
                    "ENC_THREADS": "1",
                },
            ),
            depends=list(map(lambda x: x.id, group1 + group2 + group3)),
        )
    )
    if gpu:
        # lvl4 perf gpu - not working in vm
        group4.append(
            lvl4_perfGPU := tiger.delay(
                tasks.run_test,
                args=(
                    "salmon_core_perf_tests.SalmonCorePerfTests",
                    "../../../../libs/test/salmon_core_test_python",
                    {
                        "AES_PROVIDER_TYPE": "Default",
                        "ENABLE_GPU": "true",
                        "ENC_THREADS": "1",
                    },
                ),
                depends=list(map(lambda x: x.id, group1 + group2 + group3)),
            )
        )

sched_lvl1()
sched_lvl2()
sched_lvl3()
# sched_lvl4()
