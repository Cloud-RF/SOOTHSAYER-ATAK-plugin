#!/bin/bash

VERSION=$1
mkdir /tmp/SOOTHSAYER-$VERSION
cp -r * /tmp/SOOTHSAYER-$VERSION/
rm -rf /tmp/SOOTHSAYER-$VERSION/build
rm -rf /tmp/SOOTHSAYER-$VERSION/app/build
cd /tmp
zip -r SOOTHSAYER-$VERSION.zip SOOTHSAYER-$VERSION
