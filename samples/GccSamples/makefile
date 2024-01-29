# salmon makefile

LIB_ROOT=./salmon-lib
SALMON_ROOT=$(LIB_ROOT)
TINY_AES_ROOT=$(LIB_ROOT)/tiny-AES-c

SALMON_INCLUDE=$(SALMON_ROOT)/include
TINY_AES_INCLUDE=$(TINY_AES_ROOT)

ifeq ($(ARCH), aarch64)
  CC=aarch64-linux-gnu-gcc
  CFLAGS+=-march=armv8-a+crypto
else
  ARCH=x86_64
  CC=gcc
  CFLAGS+=-maes
endif

CFLAGS+=-I$(SALMON_INCLUDE)
CFLAGS+=-I$(TINY_AES_INCLUDE)
CFLAGS+=-DUSE_TINY_AES=1 -DAES256=1
CFLAGS+=-fPIC
LIBS=-lm -lsalmon
LIB_INCLUDE=-L$(LIB_ROOT)/lib

SRC=salmon_sample.c

_SALMON_DEPS = salmon.h salmon-aes-intr/salmon-aes-intr.h
SALMON_DEPS = $(patsubst %,$(SALMON_INCLUDE)/%,$(_SALMON_DEPS))

_TINY_AES_DEPS = aes.h
TINY_AES_DEPS = $(patsubst %,$(TINY_AES_INCLUDE)/%,$(_TINY_AES_DEPS))

OBJECT := $(SRC:.c=.o)

.PHONY: all
all: salmon_sample

%.o: %.c $(SALMON_DEPS) $(TINY_AES_DEPS)
	$(CC) -c -o $@ $< $(CFLAGS)

salmon_sample: $(OBJECT)
	cp -f $(LIB_ROOT)/lib/* .
	$(CC) -o $@ $^ $(CFLAGS) $(LIB_INCLUDE) $(LIBS)
	
.PHONY: all

clean:
	rm -rf *.o *.so *.dll *.dylib salmon_sample