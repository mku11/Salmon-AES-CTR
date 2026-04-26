from tasktiger import TaskTiger
import tasks

langs = ["PYTHON", "JAVA", "JS", "CSHARP"]
tiger = TaskTiger()
LNG = None

test_cls = {
    ("PYTHON", "NATIVE"): "salmon_native_tests.SalmonNativeTests",
    ("PYTHON", "CORE"): "salmon_core_tests.SalmonCoreTests",
    ("PYTHON", "FS"): "salmon_fs_tests.SalmonFSTests",
    ("PYTHON", "FSHTTP"): "salmon_fs_http_tests.SalmonFSHttpTests",
    ("PYTHON", "PERF"): "salmon_core_perf_tests.SalmonCorePerfTests",
}

test_path = {
    ("PYTHON", "NATIVE"): "../../libs/test/salmon_native_test_python",
    ("PYTHON", "CORE"): "../../libs/test/salmon_core_test_python",
    ("PYTHON", "FS"): "../../libs/test/salmon_fs_test_python",
    ("PYTHON", "PERF"): "../../libs/test/salmon_core_test_python",
}

lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2 = None, None, None, None
lvl1_aesGpu1, lvl1_aesGpu2 = None, None
lvl2_default1, lvl2_default2 = None, None
group0, group1, group2, group3 = [], [], [], []
timeout = 30 * 60


def sched_lvl1(cl, path, gpu=False):
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl1_aesGpu1, lvl1_aesGpu2

    # LVL1 native
    group1 = []
    group1.append(
        lvl1_aes1 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
                path,
                {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "1"},
            ),
        )
    )
    group1.append(
        lvl1_aes2 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
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
                cl,
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
                cl,
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
                    cl,
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
                    cl,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "true",
                        "ENC_THREADS": "2",
                    },
                ),
            )
        )


def sched_lvl2(cl, path, gpu=False):
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl2_default1, lvl2_default2

    # # LVL 2 core
    group2 = []
    # lvl2 core default
    group2.append(
        lvl2_default1 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
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
                cl,
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
                cl,
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
                cl,
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
                cl,
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
                cl,
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
                    cl,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "true",
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
                    cl,
                    path,
                    {
                        "AES_PROVIDER_TYPE": "AesGPU",
                        "ENABLE_GPU": "true",
                        "ENC_THREADS": "2",
                    },
                ),
                depends=list(map(lambda x: x.id, [lvl1_aesGpu1, lvl1_aesGpu2])),
            )
        )


def sched_lvl3(cl, clHttp, path):
    global lvl2_default1, lvl2_default2

    # # LVL 3 fs
    group3 = []
    # lvl2 fs local
    group3.append(
        lvl3_fsDefault1 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
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
                cl,
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
                clHttp,
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
                clHttp,
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
                cl,
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
                cl,
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
            depends=list(map(lambda x: x.id, [lvl2_default1, lvl2_default2])),
        )
    )


def sched_lvl4(cl, path, gpu=False):
    global group1, group2, group3

    # # LVL 4 perf - no concurrency
    group4 = []
    group4.append(
        lvl4_perf1 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": str(gpu).lower(),
                    "ENC_THREADS": "1",
                },
            ),
            depends=list(map(lambda x: x.id, group1 + group2 + group3)),
        )
    )
    group4.append(
        lvl4_perf2 := tiger.delay(
            tasks.run_test,
            args=(
                cl,
                path,
                {
                    "AES_PROVIDER_TYPE": "Default",
                    "ENABLE_GPU": str(gpu).lower(),
                    "ENC_THREADS": "2",
                },
            ),
            depends=list(map(lambda x: x.id, group1 + group2 + group3 + [lvl4_perf1])),
        )
    )


def sched(lang: str, gpu: bool = False):
    if lang not in langs:
        print("Supported languages: ", langs)
        return
    sched_lvl1(test_cls[(lang,"NATIVE")], test_path[(lang,"NATIVE")])
    sched_lvl2(test_cls[(lang,"CORE")], test_path[(lang,"CORE")])
    sched_lvl3(test_cls[(lang,"FS")], test_cls[(lang,"FSHTTP")], test_path[(lang,"FS")])
    sched_lvl4(test_cls[(lang, "PERF")], test_path[(lang, "PERF")], gpu)
