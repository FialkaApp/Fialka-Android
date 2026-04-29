MONERO_TAG = v0.18.3.4

all: arm64-v8a x86_64 include/wallet2_api.h

arm64-v8a: android64.Dockerfile
	-rm -rf arm64-v8a
	-docker container rm fialka-monero-arm64 -f
	docker build -f android64.Dockerfile --build-arg MONERO_TAG=$(MONERO_TAG) -t fialka-monero-arm64 .
	docker create -it --name fialka-monero-arm64 fialka-monero-arm64 bash
	docker start fialka-monero-arm64
	docker exec fialka-monero-arm64 bash -c "cd /src/build/release && make wallet_api -j$(nproc)"
	mkdir -p arm64-v8a/monero
	docker cp fialka-monero-arm64:/opt/android/prefix/lib/. arm64-v8a/
	docker cp fialka-monero-arm64:/src/build/release/lib/. arm64-v8a/monero/
	docker rm -f fialka-monero-arm64

x86_64: android64_x86.Dockerfile
	-rm -rf x86_64
	-docker container rm fialka-monero-x86_64 -f
	docker build -f android64_x86.Dockerfile --build-arg MONERO_TAG=$(MONERO_TAG) -t fialka-monero-x86_64 .
	docker create -it --name fialka-monero-x86_64 fialka-monero-x86_64 bash
	docker start fialka-monero-x86_64
	docker exec fialka-monero-x86_64 bash -c "cd /src/build/release && make wallet_api -j$(nproc)"
	mkdir -p x86_64/monero
	docker cp fialka-monero-x86_64:/opt/android/prefix/lib/. x86_64/
	docker cp fialka-monero-x86_64:/src/build/release/lib/. x86_64/monero/
	docker rm -f fialka-monero-x86_64

include/wallet2_api.h: android64.Dockerfile
	-docker container rm fialka-monero-arm64-api -f
	docker build -f android64.Dockerfile --build-arg MONERO_TAG=$(MONERO_TAG) -t fialka-monero-arm64 .
	docker create -it --name fialka-monero-arm64-api fialka-monero-arm64 bash
	mkdir -p include
	docker cp fialka-monero-arm64-api:/src/src/wallet/api/wallet2_api.h include/wallet2_api.h
	docker rm -f fialka-monero-arm64-api

clean:
	-rm -rf arm64-v8a x86_64 include
