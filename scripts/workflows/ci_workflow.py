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

lvl0_webfsStart,lvl0_build = None,None
lvl1_aes1, lvl1_aes2, lvl1_aesIntr1, lvl1_aesIntr2 = None, None, None, None
lvl1_aesGpu1, lvl1_aesGpu2 = None, None
lvl2_default1, lvl2_default2 = None, None
group0_build, group0_services, group1, group2, group3, group4 = [], [], [], [], [], []
timeout = 30 * 60


def get_build_cmd(module_type: str, gpu: bool, env: dict):
    if module_type == "PYTHON":
        return ["/bin/bash", "./build_python.sh"]
    elif module_type == "JAVA":
        if gpu:
            return ["/bin/bash", "./build_java_nativegpu.sh"]
        else:
            return ["/bin/bash", "./build_java_native.sh"]
    elif module_type == "CSHARP":
        return ["/bin/bash", "./build_dotnet_debug.sh"]
    elif module_type == "JS":
        return ["/bin/bash", "./build_ts_js.sh"]
    elif module_type == "GCC":
        return ["/bin/bash", "./build_gcc_linux_x86_64.sh"]

def get_test_cmd(lang: str, cl: str, suite: str, env: dict):
    if lang == "PYTHON":
        return ["python", "-m", "unittest", "-v", cl]
    elif lang == "JAVA":
        return ["./gradlew", suite, "--tests", cl, "-i", "--rerun-tasks"] + [f"-D{k}={v}" for k, v in env.items()]
    elif lang == "CSHARP": # use ClassName=NameSpace.ClassName or FullyQualifiedName=NameSpace.ClassName.MethodName
        return ["dotnet", "test", "--filter", f"ClassName={cl}", 
                "--logger:\"console;verbosity=detailed\"", 
                "-c","Debug"]
    elif lang == "JS":
        return ["npm","run","test", "--", suite, f"-t={cl}"] + [f"{k}={v}" for k, v in env.items()]

# build module
def sched_lvl0a(module_type, gpu: bool = False):    
    env={}
    # lvl0 build module
    group0_build.append(
        lvl0_build := tiger.delay(
            tasks.run_build,
            args=(module_type + ".lvl0_build", get_build_cmd(module_type, gpu, env), "../deploy", env),
            hard_timeout=timeout
        )
    )

# start services
def sched_lvl0b(lang, gpu: bool = False):
    global lvl0_webfsStart

    # lvl0 web service build
    cmd = ["/bin/bash", "./setup_webfs.sh"]
    group0_services.append(
        lvl0_webfsBuild := tiger.delay(
            tasks.setup_ws_server,
            args=(cmd,"../misc", {}),
        )
    )
    
    cmd = ["/bin/bash", "./start_ws_server.sh"]
    group0_services.append(
        lvl0_webfsStart := tiger.delay(
            tasks.start_ws_server,
            args=(cmd,"../misc", {}),
            depends=list(map(lambda x: x.id, [lvl0_webfsBuild])),
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

    env = {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "1", "NODE_OPTIONS": "--experimental-vm-modules"}
    group1.append(
        lvl1_aes1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl1_aes1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )
    env = {"AES_PROVIDER_TYPE": "Aes", "ENABLE_GPU": "false", "ENC_THREADS": "2", "NODE_OPTIONS": "--experimental-vm-modules"}
    group1.append(
        lvl1_aes2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl1_aes2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )

    # lvl1 intrinsics
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group1.append(
        lvl1_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl1_aesIntr1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group1.append(
        lvl1_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl1_aesIntr2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )

    if gpu:
        # lvl1 gpu - opencl not working on vm
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "1",
            "NODE_OPTIONS": "--experimental-vm-modules"
        }
        group1.append(
            lvl1_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=(lang + ".lvl1_aesGpu1", get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, group0_build)),
            )
        )
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "2",
            "NODE_OPTIONS": "--experimental-vm-modules"
        }
        group1.append(
            lvl1_aesGpu2 := tiger.delay(
                tasks.run_test,
                args=(lang + ".lvl1_aesGpu2", get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, group0_build)),
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
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_default1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_default1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_default2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_default2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build)),
        )
    )
    # lvl2 core aes native
    env = {
        "AES_PROVIDER_TYPE": "Aes",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_aes1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_aes1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl1_aes1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Aes",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2", 
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_aes2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_aes2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl1_aes1, lvl1_aes2])),
        )
    )

    # lvl2 core intrinsics
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_aesIntr1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_aesIntr1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl1_aesIntr1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "AesIntrinsics",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group2.append(
        lvl2_aesIntr2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl2_aesIntr2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl1_aesIntr1, lvl1_aesIntr2])),
        )
    )

    # lvl2 core gpu - not working on vm
    if gpu:
        env = {
            "AES_PROVIDER_TYPE": "AesGPU", 
            "ENABLE_GPU": "true", 
            "ENC_THREADS": "1",
            "NODE_OPTIONS": "--experimental-vm-modules"
        }
        group2.append(
            lvl2_aesGpu1 := tiger.delay(
                tasks.run_test,
                args=(lang + ".lvl2_aesGpu1",get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, group0_build + [lvl1_aesGpu1])),
            )
        )
        env = {
            "AES_PROVIDER_TYPE": "AesGPU",
            "ENABLE_GPU": "true",
            "ENC_THREADS": "2",
            "NODE_OPTIONS": "--experimental-vm-modules"
        }
        group2.append(
            lvl2_aesGpu2 := tiger.delay(
                tasks.run_test,
                args=(lang + ".lvl2_aesGpu2",get_test_cmd(lang, cl, suite, env), path, env),
                depends=list(map(lambda x: x.id, group0_build + [lvl1_aesGpu1, lvl1_aesGpu2])),
            )
        )


def sched_lvl3a(lang):
    global lvl2_default1, lvl2_default2
    global lvl0_webfsStart

    cl = test_cls[(lang, "FS")]
    clHttp = test_cls[(lang, "FSHTTP")]
    path = test_path[(lang, "FS")]
    suite = test_suite.get((lang, "FS"), "")

    # # LVL 3 fs
    # lvl3 fs local
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "TEST_MODE": "Local",
        "TEST_DIR": "/tmp/salmon/test",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_fsDefault1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_fsDefault1",get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl2_default1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "TEST_MODE": "Local",
        "TEST_DIR": "/tmp/salmon/test",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_fsDefault2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_fsDefault2",get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group0_build + [lvl2_default1, lvl2_default2])),
        )
    )


def sched_lvl3b(lang):
    global lvl2_default1, lvl2_default2
    global lvl0_webfsStart

    cl = test_cls[(lang, "FS")]
    clHttp = test_cls[(lang, "FSHTTP")]
    path = test_path[(lang, "FS")]
    suite = test_suite.get((lang, "FS"), "")

    # # LVL 3 fs    
    # lvl3 fs http
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "TEST_MODE": "Http",
        "TEST_DIR": "/var/www/salmon/test",
        "HTTP_SERVER_URL": "http://localhost",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_httpDefault1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_httpDefault1",get_test_cmd(lang, clHttp, suite, env), path, env),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, group0_build + [lvl2_default1])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "TEST_MODE": "Http",
        "TEST_DIR": "/var/www/salmon/test",
        "HTTP_SERVER_URL": "http://localhost",
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_httpDefault2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_httpDefault2", get_test_cmd(lang, clHttp, suite, env), path, env),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, group0_build + [lvl2_default1, lvl2_default2])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "1",
        "TEST_MODE": "WebService",
        "TEST_DIR": "/tmp/salmon/test",
        "WS_SERVER_URL": "http://localhost:8081", # avoid ports that are being used
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_wsDefault1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_wsDefault1", get_test_cmd(lang, cl, suite, env), path, env),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, group0_build + [lvl0_webfsStart, lvl2_default1])),
            # depends=list(map(lambda x: x.id, group0_build + [lvl0_webfsStart])),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": "false",
        "ENC_THREADS": "2",
        "TEST_MODE": "WebService",
        "TEST_DIR": "/tmp/salmon/test",
        "WS_SERVER_URL": "http://localhost:8081", # avoid ports that are being used
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group3.append(
        lvl3_wsDefault2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl3_wsDefault2", get_test_cmd(lang, cl, suite, env), path, env),
            hard_timeout=timeout,
            depends=list(map(lambda x: x.id, group0_build + [lvl0_webfsStart, lvl2_default1, lvl2_default2])),
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
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group4.append(
        lvl4_perf1 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl4_perf1", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group1 + group2 + group3)),
        )
    )
    env = {
        "AES_PROVIDER_TYPE": "Default",
        "ENABLE_GPU": str(gpu).lower(),
        "ENC_THREADS": "2", 
        "NODE_OPTIONS": "--experimental-vm-modules"
    }
    group4.append(
        lvl4_perf2 := tiger.delay(
            tasks.run_test,
            args=(lang + ".lvl4_perf2", get_test_cmd(lang, cl, suite, env), path, env),
            depends=list(map(lambda x: x.id, group1 + group2 + group3 + [lvl4_perf1])),
        )
    )


def sched(lang: str, gpu: bool = False):
    if lang not in langs:
        print("Supported languages: ", langs)
        return
    
    sched_lvl0a("GCC", gpu) # native build
    sched_lvl0a(lang, gpu) # main build
    sched_lvl0b(lang, gpu) # services build
    sched_lvl1(lang, gpu) # native test
    sched_lvl2(lang, gpu) # core test
    sched_lvl3a(lang) # local fs test
    sched_lvl3b(lang) # remote fs drives (httpfs, webfs)
    sched_lvl4(lang, gpu) # performance
