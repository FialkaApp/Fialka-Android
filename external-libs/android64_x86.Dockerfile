FROM debian:bullseye

ARG MONERO_TAG=v0.18.3.4
ARG NPROC=4

RUN set -x && apt-get update && apt-get install -y \
    unzip automake build-essential curl file pkg-config git \
    python-is-python3 libtool libtinfo5 ca-certificates bison

WORKDIR /opt/android

# ── Android NDK r17c (x86_64, API 21) ────────────────────────────────────────

ENV ANDROID_NDK_REVISION=17c
ENV ANDROID_NDK_HASH=3f541adbd0330a9205ba12697f6d04ec90752c53d6b622101a2a8a856e816589
RUN curl -O https://dl.google.com/android/repository/android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && echo "${ANDROID_NDK_HASH}  android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip" | sha256sum -c \
    && unzip android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip \
    && rm -f android-ndk-r${ANDROID_NDK_REVISION}-linux-x86_64.zip

ENV ANDROID_NDK_ROOT=/opt/android/android-ndk-r17c
ENV PREFIX=/opt/android/prefix

ENV TOOLCHAIN_DIR=/opt/android/toolchain
RUN ${ANDROID_NDK_ROOT}/build/tools/make_standalone_toolchain.py \
         --arch x86_64 \
         --api 21 \
         --install-dir ${TOOLCHAIN_DIR} \
         --stl=libc++

# ── CMake 3.14 ────────────────────────────────────────────────────────────────

ARG CMAKE_VERSION=3.14.6
ARG CMAKE_HASH=82e08e50ba921035efa82b859c74c5fbe27d3e49a4003020e3c77618a4e912cd
RUN cd /usr \
    && curl -L -O https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && echo "${CMAKE_HASH}  cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz" | sha256sum -c \
    && tar -xzf cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
    && rm -f cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz
ENV PATH=/usr/cmake-${CMAKE_VERSION}-Linux-x86_64/bin:$PATH

# ── Boost 1.70 ────────────────────────────────────────────────────────────────

ARG BOOST_VERSION=1_70_0
ARG BOOST_VERSION_DOT=1.70.0
ARG BOOST_HASH=430ae8354789de4fd19ee52f3b1f739e1fba576f0aded0897c3c2bc00fb38778

# Save host PATH before prepending cross-compiler (needed by bootstrap.sh)
ENV HOST_PATH=$PATH
ENV PATH=${TOOLCHAIN_DIR}/x86_64-linux-android/bin:${TOOLCHAIN_DIR}/bin:$PATH

RUN curl -L -o boost_${BOOST_VERSION}.tar.bz2 \
    https://archives.boost.io/release/${BOOST_VERSION_DOT}/source/boost_${BOOST_VERSION}.tar.bz2 \
    && echo "${BOOST_HASH}  boost_${BOOST_VERSION}.tar.bz2" | sha256sum -c \
    && tar -xvf boost_${BOOST_VERSION}.tar.bz2 \
    && rm -f boost_${BOOST_VERSION}.tar.bz2

# libiconv (needed by boost locale)
ENV ICONV_VERSION=1.16
ENV ICONV_HASH=e6a1b1b589654277ee790cce3734f07876ac4ccfaecbee8afa0b649cf529cc04
RUN curl -O http://ftp.gnu.org/pub/gnu/libiconv/libiconv-${ICONV_VERSION}.tar.gz \
    && echo "${ICONV_HASH}  libiconv-${ICONV_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf libiconv-${ICONV_VERSION}.tar.gz \
    && rm -f libiconv-${ICONV_VERSION}.tar.gz \
    && cd libiconv-${ICONV_VERSION} \
    && CC=clang CXX=clang++ ./configure \
         --build=x86_64-linux-gnu --host=x86_64-linux-android \
         --prefix=${PREFIX} --disable-rpath \
    && make -j${NPROC} && make install

# bootstrap.sh builds the b2 host tool — must use HOST_PATH, not cross-compiler
RUN cd boost_${BOOST_VERSION} \
    && PATH=${HOST_PATH} ./bootstrap.sh --prefix=${PREFIX} \
    && ./b2 --build-type=minimal link=static runtime-link=static \
        --with-chrono --with-date_time --with-filesystem --with-program_options \
        --with-regex --with-serialization --with-system --with-thread --with-locale \
        --build-dir=android --stagedir=android \
        toolset=clang threading=multi threadapi=pthread target-os=android \
        -sICONV_PATH=${PREFIX} install -j${NPROC}

# ── Zlib ──────────────────────────────────────────────────────────────────────

ENV ZLIB_VERSION=1.3.1
ENV ZLIB_HASH=9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23
RUN curl -L -O https://github.com/madler/zlib/releases/download/v${ZLIB_VERSION}/zlib-${ZLIB_VERSION}.tar.gz \
    && echo "${ZLIB_HASH}  zlib-${ZLIB_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf zlib-${ZLIB_VERSION}.tar.gz && rm zlib-${ZLIB_VERSION}.tar.gz \
    && mv zlib-${ZLIB_VERSION} zlib \
    && cd zlib && CC=clang CXX=clang++ ./configure --static && make -j${NPROC}

# ── OpenSSL 3.0.5 ─────────────────────────────────────────────────────────────

ARG OPENSSL_VERSION=3.0.5
ARG OPENSSL_HASH=aa7d8d9bef71ad6525c55ba11e5f4397889ce49c2c9349dcea6d3e4f0b024a7a
RUN curl -L -O https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz \
    && echo "${OPENSSL_HASH}  openssl-${OPENSSL_VERSION}.tar.gz" | sha256sum -c \
    && tar -xzf openssl-${OPENSSL_VERSION}.tar.gz \
    && rm openssl-${OPENSSL_VERSION}.tar.gz \
    && cd openssl-${OPENSSL_VERSION} \
    && PATH=${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin:${HOST_PATH} \
       ./Configure android-x86_64 -D__ANDROID_API__=21 -static no-shared no-tests \
         --with-zlib-include=/opt/android/zlib \
         --prefix=${PREFIX} --openssldir=${PREFIX} \
    && make -j${NPROC} && make install_sw

# ── libunbound ────────────────────────────────────────────────────────────────

ARG LIBEXPAT_VERSION=R_2_4_8
ARG LIBEXPAT_HASH=3bab6c09bbe8bf42d84b81563ddbcf4cca4be838
RUN git clone https://github.com/libexpat/libexpat.git -b ${LIBEXPAT_VERSION} \
    && cd libexpat/expat \
    && test `git rev-parse HEAD` = ${LIBEXPAT_HASH} || exit 1 \
    && ./buildconf.sh \
    && CC=clang CXX=clang++ ./configure \
         --prefix=${PREFIX} --host=x86_64-linux-android \
         --enable-static --disable-shared \
    && make -j${NPROC} && make install

ARG LIBUNBOUND_HASH=903538c76e1d8eb30d0814bb55c3ef1ea28164e8
RUN git clone https://github.com/NLnetLabs/unbound.git \
    && cd unbound \
    && git checkout ${LIBUNBOUND_HASH} \
    && CC=clang CXX=clang++ ./configure \
         --prefix=${PREFIX} --host=x86_64-linux-android \
         --enable-static --disable-shared --disable-flto \
         --with-ssl=${PREFIX} --with-libexpat=${PREFIX} \
         --disable-gost --disable-ecdsa --disable-dsa \
         --without-pythonmodule --without-pyunbound \
         ac_cv_func_getentropy=no ac_cv_func_explicit_bzero=no \
    && make -j${NPROC} && make install

# ── utf8proc ─────────────────────────────────────────────────────────────────

RUN git clone https://github.com/JuliaStrings/utf8proc -b v2.8.0 \
    && cd utf8proc \
    && git reset --hard 1cb28a66ca79a0845e99433fd1056257456cef8b \
    && mkdir build && cd build \
    && CC=clang CXX=clang++ cmake -DCMAKE_INSTALL_PREFIX=${PREFIX} .. \
    && make -j${NPROC} && make install

# ── libzmq 4.3.4 ─────────────────────────────────────────────────────────────

ARG LIBZMQ_VERSION=4.3.4
RUN git clone https://github.com/zeromq/libzmq.git -b v${LIBZMQ_VERSION} --depth 1 \
    && cd libzmq \
    && ./autogen.sh \
    && CC=clang CXX=clang++ ./configure \
         --prefix=${PREFIX} --host=x86_64-linux-android \
         --enable-static --disable-shared --disable-libbsd \
         --without-docs \
    && make -j${NPROC} && make install

# ── libsodium ─────────────────────────────────────────────────────────────────

ARG LIBSODIUM_VERSION=1.0.18
RUN curl -L -O https://github.com/jedisct1/libsodium/releases/download/${LIBSODIUM_VERSION}-RELEASE/libsodium-${LIBSODIUM_VERSION}.tar.gz \
    && tar -xzf libsodium-${LIBSODIUM_VERSION}.tar.gz \
    && rm libsodium-${LIBSODIUM_VERSION}.tar.gz \
    && cd libsodium-${LIBSODIUM_VERSION} \
    && CC=clang CXX=clang++ ./configure \
         --prefix=${PREFIX} --host=x86_64-linux-android \
         --enable-static --disable-shared \
    && make -j${NPROC} && make install

# ── Monero (official) ─────────────────────────────────────────────────────────

RUN git clone --recursive https://github.com/monero-project/monero.git /src \
    && cd /src \
    && git checkout ${MONERO_TAG} \
    && git submodule update --init --force \
    && sed -i 's/add_library(wallet_api SHARED/add_library(wallet_api STATIC/' src/wallet/api/CMakeLists.txt

RUN cd /src \
    && mkdir -p build/release/translations \
    && cd build/release/translations \
    && PATH=${HOST_PATH} cmake ../../../translations \
    && PATH=${HOST_PATH} make \
    && cd /src/build/release \
    && CC=x86_64-linux-android-clang \
       CXX=x86_64-linux-android-clang++ \
       cmake -DBUILD_TESTS=OFF -DARCH="x86-64" -DSTATIC=ON -DBUILD_64=ON \
             -DCMAKE_BUILD_TYPE=Release -DANDROID=true -DBUILD_TAG="android-x86_64" \
             -DCMAKE_SYSTEM_NAME="Android" \
             -DCMAKE_ANDROID_STANDALONE_TOOLCHAIN="${TOOLCHAIN_DIR}" \
             -DCMAKE_ANDROID_ARCH_ABI="x86_64" \
             -DCMAKE_INCLUDE_PATH="${PREFIX}/include" \
             -DCMAKE_LIBRARY_PATH="${PREFIX}/lib" \
             ../.. \
    && PATH=${HOST_PATH} make -j${NPROC}

RUN cd /src/build/release \
    && find . -path ./lib -prune -o -name '*.a' -exec cp '{}' lib \;
