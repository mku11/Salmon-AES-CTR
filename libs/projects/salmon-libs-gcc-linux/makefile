# salmon makefile

SRC_ROOT=../../src/c
SALMON_ROOT=$(SRC_ROOT)/salmon
SALMON_JNI_ROOT=$(SRC_ROOT)/salmon-jni
TINY_AES_ROOT=$(SRC_ROOT)/tiny-AES-c

SALMON_INCLUDE=$(SALMON_ROOT)/include
SALMON_JNI_INCLUDE=$(SALMON_JNI_ROOT)/include
TINY_AES_INCLUDE=$(TINY_AES_ROOT)

$(info Usage:)
$(info make ARCH=<value> USE_TINY_AES=<value> ENABLE_JNI=<value>)
$(info ARCH is the target architecture, value: x86_64, aarch64)
$(info USE_TINY_AES to include TinyAES, value: 0 to disable, 1 to enable)
$(info ENABLE_JNI to use this lib from Java, value: 0 to disable, 1 to enable)
$(info Example: make ARCH=x86_64 USE_TINY_AES=1 ENABLE_JNI=1)
$(info )

ifeq ($(ARCH),aarch64)
  CC=aarch64-linux-gnu-gcc
  CFLAGS+=-march=armv8-a+crypto
else
  ARCH=x86_64
  CC=gcc
  CFLAGS+=-maes
endif

CFLAGS+=-I$(SALMON_INCLUDE)
CFLAGS+=-fPIC
LIBS=-lm

ifeq ($(USE_TINY_AES),1)
  CFLAGS+=-I$(TINY_AES_INCLUDE)
  CFLAGS+=-DUSE_TINY_AES=1 -DAES256=1
endif

ifeq ($(ENABLE_JNI),1)
  CFLAGS+=-I$(SALMON_JNI_INCLUDE) -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
endif

OBJ_DIR=obj
LIB_DIR =lib/$(ARCH)

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
all: libsalmon.so

%.o: %.c $(SALMON_DEPS)
	@mkdir -p $(subst $(SRC_ROOT),$(OBJ_DIR),$(@D))
	$(CC) -c -o $(subst $(SRC_ROOT),$(OBJ_DIR),$@) $< $(CFLAGS)

libsalmon.so: $(OBJECT)
	@mkdir -p $(LIB_DIR)
	$(CC) -shared -o $(LIB_DIR)/$@ $(subst $(SRC_ROOT),$(OBJ_DIR),$^) $(CFLAGS) $(LIBS)

.PHONY: all

clean:
	rm -rf $(OBJ_DIR)/* lib/* 