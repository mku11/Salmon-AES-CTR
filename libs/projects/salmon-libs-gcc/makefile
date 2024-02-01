# salmon makefile
SRC_ROOT=../../src/c
SALMON_ROOT=$(SRC_ROOT)/salmon
SALMON_JNI_ROOT=$(SRC_ROOT)/salmon-jni
TINY_AES_ROOT=$(SRC_ROOT)/tiny-AES-c
OUTPUT_ROOT=../../../output

SALMON_INCLUDE=$(SALMON_ROOT)/include
SALMON_JNI_INCLUDE=$(SALMON_JNI_ROOT)/include
TINY_AES_INCLUDE=$(TINY_AES_ROOT)

$(info Usage:)
$(info make PLATFORM=<value> ARCH=<value> USE_TINY_AES=<value> ENABLE_JNI=<value>)
$(info PLATFORM is the target OS, value: win, linux, macos)
$(info ARCH is the target architecture, value: x86_64, aarch64)
$(info USE_TINY_AES to include TinyAES, value: 0 to disable, 1 to enable)
$(info ENABLE_JNI to use this lib from Java, value: 0 to disable, 1 to enable)
$(info Example: make PLATFORM=win ARCH=x86_64 USE_TINY_AES=1 ENABLE_JNI=1)
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

VERSION=1.0.6-SNAPSHOT
PACKAGES_DIR=packages
PACKAGE_NAME=salmon-$(PLATFORM)-$(ARCH).$(VERSION)
PACKAGE_FILE=$(PACKAGE_NAME).tar.gz
OUTPUT_DIR=$(OUTPUT_ROOT)/$(PLATFORM)
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

ifeq ($(USE_TINY_AES),1)
  CFLAGS+=-I$(TINY_AES_INCLUDE)
  CFLAGS+=-DUSE_TINY_AES=1 -DAES256=1
endif

ifeq ($(ENABLE_JNI),1)
  CFLAGS+=-I$(SALMON_JNI_INCLUDE) -I"$(JAVA_HOME)/include"
  ifeq ($(PLATFORM),win)
	CFLAGS+=-I"$(JAVA_HOME)/include/win32"
    CFLAGS+=-D__int64=long
  endif
  ifeq ($(PLATFORM),linux)
	CFLAGS+=-I"$(JAVA_HOME)/include/linux"
  endif
endif

OBJ_DIR=obj
MAIN_LIB_DIR=lib
LIB_DIR =$(MAIN_LIB_DIR)/$(ARCH)

_SRC=$(wildcard ../../src/c/salmon/*.c ../../src/c/salmon/salmon-aes-intr/*.c)

ifeq ($(USE_TINY_AES),1)
  _SRC+=$(wildcard ../../src/c/tiny-AES-c/*.c)
endif

ifeq ($(ENABLE_JNI),1)
  _SRC+=../../src/c/salmon-jni/salmon-jni.c
endif

SRC=$(filter-out $(wildcard $(TINY_AES_ROOT)/test.c),$(_SRC))

_SALMON_DEPS = salmon.h salmon-aes-intr/salmon-aes-intr.h
SALMON_DEPS = $(patsubst %,$(SALMON_INCLUDE)/%,$(_SALMON_DEPS))

_TINY_AES_DEPS = aes.h
TINY_AES_DEPS = $(patsubst %,$(TINY_AES_INCLUDE)/%,$(_TINY_AES_DEPS))

OBJECT := $(SRC:.c=.o)

.PHONY: all
all: $(LIB_NAME)

%.o: %.c $(SALMON_DEPS) $(TINY_AES_DEPS)
	@mkdir -p $(subst $(SRC_ROOT),$(OBJ_DIR),$(@D))
	$(CC) -c -o $(subst $(SRC_ROOT),$(OBJ_DIR),$@) $< $(CFLAGS)

$(LIB_NAME): $(OBJECT)
	@mkdir -p $(LIB_DIR)
	$(CC) -shared -o $(LIB_DIR)/$@ $(subst $(SRC_ROOT),$(OBJ_DIR),$^) $(CFLAGS) $(LIBS)

package: $(LIB_NAME)
	@mkdir -p $(PACKAGES_DIR)/$(PACKAGE_NAME)
	rm -rf $(PACKAGES_DIR)/$(PACKAGE_NAME)/*
	@mkdir -p $(PACKAGES_DIR)/$(PACKAGE_NAME)/include
	cp -r $(SALMON_INCLUDE)/* $(PACKAGES_DIR)/$(PACKAGE_NAME)/include
	@mkdir -p $(PACKAGES_DIR)/$(PACKAGE_NAME)/lib
	cp $(LIB_DIR)/$(LIB_NAME) $(PACKAGES_DIR)/$(PACKAGE_NAME)/lib
	cp README.txt $(PACKAGES_DIR)/$(PACKAGE_NAME)
	cd $(PACKAGES_DIR) && tar -czvf $(PACKAGE_FILE) $(PACKAGE_NAME)/
	@mkdir -p $(OUTPUT_DIR)
	cp -f $(PACKAGES_DIR)/$(PACKAGE_FILE) $(OUTPUT_DIR)
	
.PHONY: all

clean:
	rm -rf $(OBJ_DIR)/* $(MAIN_LIB_DIR)/* $(PACKAGES_DIR)/*