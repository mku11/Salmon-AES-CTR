from tasktiger import TaskTiger
import tasks

langs = ["PYTHON", "JAVA", "JS", "CSHARP"]
tiger = TaskTiger()
LNG = None

test_cls = {
    # example for specific unit testing
    # ("PYTHON", "NATIVE"): "salmon_native_tests.SalmonNativeTests.test_encrypt_and_decrypt_native_text_compatible",
    # ("PYTHON", "CORE"): "salmon_core_tests.SalmonCoreTests.test_shouldEncryptAndDecryptText",
    # ("PYTHON", "FS"): "salmon_fs_tests.SalmonFSTests.test_import_and_search_files",
    # ("PYTHON", "FSHTTP"): "salmon_fs_http_tests.SalmonFSHttpTests.test_shouldReadFromFileTiny",
    # ("PYTHON", "PERF"): "salmon_core_perf_tests.SalmonCorePerfTests",
    
    ("PYTHON", "NATIVE"): "salmon_native_tests.SalmonNativeTests",
    ("PYTHON", "CORE"): "salmon_core_tests.SalmonCoreTests",
    ("PYTHON", "FS"): "salmon_fs_tests.SalmonFSTests",
    ("PYTHON", "FSHTTP"): "salmon_fs_http_tests.SalmonFSHttpTests",
    ("PYTHON", "PERF"): "salmon_core_perf_tests.SalmonCorePerfTests",
        
    ("JAVA", "NATIVE"): "com.mku.salmon.test.SalmonNativeTests",
    ("JAVA", "CORE"): "com.mku.salmon.test.SalmonCoreTests",
    ("JAVA", "FS"): "com.mku.salmon.test.SalmonFSTests",
    ("JAVA", "FSHTTP"): "com.mku.salmon.test.SalmonFSHttpTests",
    ("JAVA", "PERF"): "com.mku.salmon.test.SalmonCorePerfTests",
    
    ("CSHARP", "NATIVE"): "Mku.Salmon.Test.SalmonNativeTests",
    ("CSHARP", "CORE"): "Mku.Salmon.Test.SalmonCoreTests",
    ("CSHARP", "FS"): "Mku.Salmon.Test.SalmonFSTests",
    ("CSHARP", "FSHTTP"): "Mku.Salmon.Test.SalmonFSHttpTests",
    ("CSHARP", "PERF"): "Mku.Salmon.Test.SalmonCorePerfTests",
    
    ("JS", "NATIVE"): "salmon-native",
    ("JS", "CORE"): "salmon-core",
    ("JS", "FS"): "salmon-fs",
    ("JS", "FSHTTP"): "salmon-httpfs",
    ("JS", "PERF"): "salmon-core-perf",
    
}

test_path = {
    ("PYTHON", "NATIVE"): "../../libs/test/salmon_native_test_python",
    ("PYTHON", "CORE"): "../../libs/test/salmon_core_test_python",
    ("PYTHON", "FS"): "../../libs/test/salmon_fs_test_python",
    ("PYTHON", "PERF"): "../../libs/test/salmon_core_test_python",
    
    ("JAVA", "NATIVE"): "../../libs/projects/salmon-libs-gradle",
    ("JAVA", "CORE"): "../../libs/projects/salmon-libs-gradle",
    ("JAVA", "FS"): "../../libs/projects/salmon-libs-gradle",
    ("JAVA", "PERF"): "../../libs/projects/salmon-libs-gradle",
    
    ("CSHARP", "NATIVE"): "../../libs/projects/SalmonLibs.VS2022/Salmon.Test",
    ("CSHARP", "CORE"): "../../libs/projects/SalmonLibs.VS2022/Salmon.Test",
    ("CSHARP", "FS"): "../../libs/projects/SalmonLibs.VS2022/Salmon.Test",
    ("CSHARP", "PERF"): "../../libs/projects/SalmonLibs.VS2022/Salmon.Test",
    
    ("JS", "NATIVE"): "../../libs//projects/SalmonLibs.vscode",
    ("JS", "CORE"): "../../libs//projects/SalmonLibs.vscode",
    ("JS", "FS"): "../../libs//projects/SalmonLibs.vscode",
    ("JS", "PERF"): "../../libs//projects/SalmonLibs.vscode",
}

test_suite = {
    ("JAVA", "NATIVE"): ":salmon-native:test",
    ("JAVA", "CORE"): ":salmon-core:test",
    ("JAVA", "FS"): ":salmon-fs:test",
    ("JAVA", "PERF"): ":salmon-core:test",

    ("JS", "NATIVE"): "salmon-native",
    ("JS", "CORE"): "salmon-core",
    ("JS", "FS"): "salmon-fs",
    ("JS", "PERF"): "salmon-core",
}

lvl0_webfsStart = None
lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2 = None, None, None, None
lvl1_aesGpu1, lvl1_aesGpu2 = None, None
lvl2_default1, lvl2_default2 = None, None
group0, group1, group2, group3, group4 = [], [], [], [], []
timeout = 30 * 60


def get_test_cmd(lang: str, cl: str, suite: str, env: dict):
    if lang == "PYTHON":
        return ["python", "-m", "unittest", "-v", cl]
    elif lang == "JAVA":
        return ["./gradlew", suite, "--tests", cl, "-i"] + [f"-D{k}={v}" for k, v in env.items()]
    elif lang == "CSHARP":
        return ["dotnet", "test", "--filter", f"ClassName={cl}", "--no-build", 
                "--logger:\"console;verbosity=detailed\"", 
                "-c","Debug"]
    elif lang == "JS":
        return ["npm","run","test", "--", suite, f"-t=\"{cl}\""] + [f"{k}={v}" for k, v in env.items()]


def sched_lvl0(lang):
    global lvl0_webfsStart
    
    group0 = []
    # lvl0 web service
    # cmd = ["/bin/bash", "./setup_webfs.sh"]
    # group0.append(
        # lvl0_webfsSetup := tiger.delay(
            # tasks.setup_ws_server,
            # args=(cmd,"../misc", {}),
        # )
    # )
    
    cmd = ["/bin/bash", "./start_ws_server.sh"]
    group0.append(
        lvl0_webfsStart := tiger.delay(
            tasks.start_ws_server,
            args=(cmd,"../misc", {}),
        )
    )


def sched_lvl1(lang: str, gpu=False):
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl1_aesGpu1, lvl1_aesGpu2
    
    cl = test_cls[(lang, "NATIVE")]
    path = test_path[(lang, "NATIVE")]
    suite = test_suite.get((lang, "NATIVE"), "")

    # LVL1 native
    group1 = []

    env = {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "1"}
    group1.append(
        lvl1_aes1 := tiger.delay(
            tasks.run_test,
            args=("lvl1_aes1", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )
    env = {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "2"}
    group1.append(
        lvl1_aes2 := tiger.delay(
            tasks.run_test,
            args=("lvl1_aes2", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )

    # lvl1 intrinsics
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
    }
    group1.append(
        lvl1_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=("lvl1_aesIntr1", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
    }
    group1.append(
        lvl1_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=("lvl1_aesIntr2", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )

    if gpu:
        # lvl1 gpu - opencl not working on vm
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "1",
        }
        group1.append(
            lvl1_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=("lvl1_aesGpu1", get_test_cmd(lang, cl, suite, env), path, env),
            )
        )
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "2",
        }
        group1.append(
            lvl1_aesGpu2 := tiger.delay(
                tasks.run_test,
                args=("lvl1_aesGpu2", get_test_cmd(lang, cl, suite, env), path, env),
            )
        )


def sched_lvl2(lang, gpu=False):
    global lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2
    global lvl1_aesGpu1, lvl1_aesGpu2
    global lvl2_default1, lvl2_default2

    cl = test_cls[(lang, "CORE")]
    path = test_path[(lang, "CORE")]
    suite = test_suite.get((lang, "CORE"), "")

    # # LVL 2 core
    # lvl2 core default
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
    }
    group2.append(
        lvl2_default1 := tiger.delay(
            tasks.run_test,
            args=("lvl2_default1", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
    }
    group2.append(
        lvl2_default2 := tiger.delay(
            tasks.run_test,
            args=("lvl2_default2", get_test_cmd(lang, cl, suite, env), path, env),
        )
    )
    # lvl2 core aes native
    env = {
        "AES_PROVIDER_TYPE": "Aes",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
    }
    group2.append(
        lvl2_aes1 := tiger.delay(
            tasks.run_test,
            args=("lvl2_aes1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, [lvl1_aes1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Aes",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
    }
    group2.append(
        lvl2_aes2 := tiger.delay(
            tasks.run_test,
            args=("lvl2_aes2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, [lvl1_aes1, lvl1_aes2])),
        )
    )

    # lvl2 core intrinsics
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
    }
    group2.append(
        lvl2_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=("lvl2_aesIntr1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, [lvl1_aesIntr1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
    }
    group2.append(
        lvl2_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=("lvl2_aesIntr2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, [lvl1_aesIntr1, lvl1_aesIntr2])),
        )
    )

    # lvl2 core gpu - not working on vm
    if gpu:
        env = {"AES_PROVIDER_TYPE": "AesGPU", "ENABLE_GPU": "true", "ENC_THREADS": "1"}
        group2.append(
            lvl2_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=("lvl2_aesGpu1",get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, [lvl1_aesGpu1])),
            )
        )
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "2",
        }
        group2.append(
            lvl2_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=("lvl2_aesGpu1",get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, [lvl1_aesGpu1, lvl1_aesGpu2])),
            )
        )


def sched_lvl3(lang):
    global lvl2_default1, lvl2_default2
    global lvl0_webfsStart

    cl = test_cls[(lang, "FS")]
    clHttp = test_cls[(lang, "FSHTTP")]
    path = test_path[(lang, "FS")]
    suite = test_suite.get((lang, "FS"), "")

    # # LVL 3 fs
    # lvl2 fs local
    # env = {
        # "AES_PROVIDER_TYPE": "Default",
        # "ENABLE_GPU": "false",
        # "ENC_THREADS": "1",
        # "TEST_MODE": "Local",
        # "TEST_DIR": "/tmp/salmon/test",
    # }
    # group3.append(
        # lvl3_fsDefault1 := tiger.delay(
            # tasks.run_test,
            # args=("lvl3_fsDefault1",get_test_cmd(lang, cl, suite, env), path, env),
            # depends=list(map(lambda x: x.id, [lvl2_default1])),
        # )
    # )
    # env = {
        # "AES_PROVIDER_TYPE": "Default",
        # "ENABLE_GPU": "false",
        # "ENC_THREADS": "2",
        # "TEST_MODE": "Local",
        # "TEST_DIR": "/tmp/salmon/test",
    # }
    # group3.append(
        # lvl3_fsDefault2 := tiger.delay(
            # tasks.run_test,
            # args=("lvl3_fsDefault2",get_test_cmd(lang, cl, suite, env), path, env),
            # depends=list(map(lambda x: x.id, [lvl2_default1, lvl2_default2])),
        # )
    # )
    
    # lvl3 fs http
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "TEST_MODE": "Http",
        "TEST_DIR": "/var/www/salmon/test",
        "HTTP_SERVER_URL": "http://localhost",
    }
    group3.append(
        lvl3_httpDefault1 := tiger.delay(
            tasks.run_test,
            args=("lvl3_httpDefault1",get_test_cmd(lang, clHttp, suite, env), path, env),
            hard_timeout=timeout,
            # depends=list(map(lambda x: x.id, [lvl2_default1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "TEST_MODE": "Http",
        "TEST_DIR": "/var/www/salmon/test",
        "HTTP_SERVER_URL": "http://localhost",
    }
    group3.append(
        lvl3_httpDefault2 := tiger.delay(
            tasks.run_test,
            args=("lvl3_httpDefault2", get_test_cmd(lang, clHttp, suite, env), path, env),
            hard_timeout=timeout,
            # depends=list(map(lambda x: x.id, [lvl2_default1, lvl2_default2])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "TEST_MODE": "WebService",
        "TEST_DIR": "/tmp/salmon/test",
        "WS_SERVER_URL": "http://localhost:8081", # avoid ports that are being used
    }
    group3.append(
        lvl3_wsDefault1 := tiger.delay(
            tasks.run_test,
            args=("lvl3_wsDefault1", get_test_cmd(lang, cl, suite, env), path, env),
            hard_timeout=timeout,
            # depends=list(map(lambda x: x.id, [lvl0_webfsStart, lvl2_default1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "TEST_MODE": "WebService",
        "TEST_DIR": "/tmp/salmon/test",
        "WS_SERVER_URL": "http://localhost:8081", # avoid ports that are being used
    }
    group3.append(
        lvl3_wsDefault2 := tiger.delay(
            tasks.run_test,
            args=("lvl3_wsDefault2", get_test_cmd(lang, cl, suite, env), path, env),
            hard_timeout=timeout,
            # depends=list(map(lambda x: x.id, [lvl0_webfsStart, lvl2_default1, lvl2_default2])),
        )
    )


def sched_lvl4(lang, gpu=False):
    
    cl = test_cls[(lang, "PERF")]
    path = test_path[(lang, "PERF")]
    suite = test_suite.get((lang, "PERF"), "")

    # # LVL 4 perf - no concurrency
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": str(gpu).lower(),
        "ENC_THREADS": "1",
    }
    group4.append(
        lvl4_perf1 := tiger.delay(
            tasks.run_test,
            args=("lvl4_perf1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group1 + group2 + group3)),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": str(gpu).lower(),
        "ENC_THREADS": "2",
    }
    group4.append(
        lvl4_perf2 := tiger.delay(
            tasks.run_test,
            args=("lvl4_perf2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group1 + group2 + group3 + [lvl4_perf1])),
        )
    )


def sched(lang: str, gpu: bool = False):
    if lang not in langs:
        print("Supported languages: ", langs)
        return
    sched_lvl0(lang)
    # sched_lvl1(lang, gpu)
    # sched_lvl2(lang, gpu)
    sched_lvl3(lang)
    # sched_lvl4(lang, gpu)
