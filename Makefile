# ==============================================================================
# 🏛️ Janus — Sovereign Workstation & Mobile Bridge Makefile
# ==============================================================================

export ANDROID_HOME ?= $(HOME)/Android/Sdk
export JAVA_HOME ?= /usr/lib/jvm/java-1.21.0-openjdk-amd64

APK_PATH := app/build/outputs/apk/debug/app-debug.apk
PACKAGE_NAME := com.rmediatech.janus

DEVICE ?= $(shell adb devices | grep -E "device$$" | grep -v ":5555" | head -n 1 | awk '{print $$1}')
ADB_CMD := adb $(if $(DEVICE),-s $(DEVICE),)

.PHONY: all build test install run clean devices logs help

all: test build

help:
	@echo "Janus Sovereign Bridge Build & Deployment Targets:"
	@echo "  make test       - Execute full TDD++++++ unit and adversarial test suite"
	@echo "  make build      - Compile debug APK using Gradle"
	@echo "  make install    - Push and install debug APK to connected Android phone"
	@echo "  make run        - Launch Janus on connected Android device"
	@echo "  make logs       - Stream device logcat logs"
	@echo "  make devices    - List connected ADB devices (USB & Wi-Fi)"
	@echo "  make clean      - Clean build caches and outputs"

test:
	JAVA_HOME=$(JAVA_HOME) ./gradlew testDebugUnitTest

build:
	JAVA_HOME=$(JAVA_HOME) ./gradlew assembleDebug

install:
	@if [ ! -f $(APK_PATH) ]; then \
		echo "APK not found. Building first..."; \
		JAVA_HOME=$(JAVA_HOME) ./gradlew assembleDebug; \
	fi
	@echo "Installing Janus to device: $(DEVICE)"
	$(ADB_CMD) install -r $(APK_PATH)

run: install
	@echo "Launching Janus on device: $(DEVICE)"
	$(ADB_CMD) shell am start -n $(PACKAGE_NAME)/.MainActivity

devices:
	adb devices -l

logs:
	$(ADB_CMD) logcat -v color | grep -E "($(PACKAGE_NAME)|Janus)"

clean:
	JAVA_HOME=$(JAVA_HOME) ./gradlew clean
