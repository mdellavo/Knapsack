#!/bin/sh

MODULE="Knapsack"

TAG=$1

if [ -z "${TAG}" ]
then
    echo "USAGE: build-release.sh v1.0.1-2"
    exit 1
fi

VERSION=${TAG#v}
VERSION_NAME=${VERSION%-*}
VERSION_CODE=${VERSION#*-}

git diff --exit-code > /dev/null
if [ $? -ne 0 ]
then
    echo "WARNING: branch is not clean"
    #exit 1
fi

BUILD_PATH="./builds"

if [ ! -e "${BUILD_PATH}" ]
then
    mkdir "${BUILD_PATH}"
fi

APK_PATH="${BUILD_PATH}/${MODULE}-${TAG}.apk"

echo "------------------------------------------------------------------"
echo "Building ${MODULE} - name: ${VERSION_NAME} - code: ${VERSION_CODE}"
echo "------------------------------------------------------------------"

./KnapsackApp/gradlew -p KnapsackApp -PversionCode="${VERSION_CODE}" -PversionName="${VERSION_NAME}" --no-daemon clean assembleRelease

if [ $? -ne 0 ]
then
   echo
   echo "*** BUILD FAILED!!!"
   exit 1
fi

git tag -d ${TAG} 2>&1 >& /dev/null
git tag -a ${TAG} -m "release - ${VERSION}"

cp -f "KnapsackApp/app/build/outputs/apk/app-release.apk" ${APK_PATH}

echo
echo "Build Complete -> ${APK_PATH}"

open -R "${BUILDS_PATH}"
