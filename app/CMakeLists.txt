cmake_minimum_required(VERSION 3.4.1)

message(STATUS "ABI = ${ANDROID_ABI}")

project(fialka_monero)

# ── Source ────────────────────────────────────────────────────────────────────

add_library(fialka_monero SHARED
    src/main/cpp/fialka_jni.cpp)

# ── External libs path ────────────────────────────────────────────────────────

set(EXTERNAL_LIBS_DIR ${CMAKE_SOURCE_DIR}/../external-libs)

# ── Boost ─────────────────────────────────────────────────────────────────────

foreach(boost_lib chrono date_time filesystem program_options regex
                  serialization system thread wserialization)
    add_library(boost_${boost_lib} STATIC IMPORTED)
    set_target_properties(boost_${boost_lib} PROPERTIES IMPORTED_LOCATION
        ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libboost_${boost_lib}.a)
endforeach()

# ── OpenSSL ───────────────────────────────────────────────────────────────────

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libcrypto.a)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libssl.a)

# ── Monero ────────────────────────────────────────────────────────────────────

foreach(monero_lib
    wallet_api wallet
    cryptonote_core cryptonote_basic cryptonote_format_utils_basic
    mnemonics ringct ringct_basic
    net common cncrypto
    blockchain_db lmdb
    easylogging epee blocks checkpoints
    device device_trezor multisig version
    randomx hardforks rpc_base wallet-crypto)
    add_library(${monero_lib} STATIC IMPORTED)
    set_target_properties(${monero_lib} PROPERTIES IMPORTED_LOCATION
        ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/monero/lib${monero_lib}.a)
endforeach()

# ── Unbound ───────────────────────────────────────────────────────────────────

add_library(unbound STATIC IMPORTED)
set_target_properties(unbound PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libunbound.a)

add_library(utf8proc STATIC IMPORTED)
set_target_properties(utf8proc PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libutf8proc.a)

add_library(sodium STATIC IMPORTED)
set_target_properties(sodium PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libsodium.a)

add_library(zmq STATIC IMPORTED)
set_target_properties(zmq PROPERTIES IMPORTED_LOCATION
    ${EXTERNAL_LIBS_DIR}/${ANDROID_ABI}/libzmq.a)

# ── System ────────────────────────────────────────────────────────────────────

find_library(log-lib log)

include_directories(${EXTERNAL_LIBS_DIR}/include)

# ── Link ──────────────────────────────────────────────────────────────────────

if(${ANDROID_ABI} STREQUAL "x86_64")
    set(EXTRA_LIBS wallet-crypto)
else()
    set(EXTRA_LIBS "")
endif()

target_link_libraries(fialka_monero

    wallet_api
    wallet
    cryptonote_core
    cryptonote_basic
    cryptonote_format_utils_basic
    utf8proc
    mnemonics
    ringct
    ringct_basic
    net
    common
    cncrypto
    blockchain_db
    lmdb
    easylogging
    unbound
    epee
    blocks
    checkpoints
    device
    device_trezor
    multisig
    version
    randomx
    hardforks
    rpc_base
    ${EXTRA_LIBS}

    boost_chrono
    boost_date_time
    boost_filesystem
    boost_program_options
    boost_regex
    boost_serialization
    boost_system
    boost_thread
    boost_wserialization

    ssl
    crypto
    sodium
    zmq

    ${log-lib}
)
