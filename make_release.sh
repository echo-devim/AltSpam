#!/usr/bin/env bash

if [ $# -eq 0 ]
  then
      echo "No arguments supplied. Specify 1 for Linux release or 2 for Windows release (e.g. $0 2)"
fi

mkdir release
echo "Compiling Java application.."
mvn package
mv target/AltSpam-1.0-SNAPSHOT.jar release/altspam.jar
rm -rf target
echo "Compiling libAltSpam..."
cd libAltSpam
bash compile.sh
cd ..
mv libAltSpam/altspam* release/
rm libAltSpam/libAltSpam.so
cp icon* release/
echo "Copying dependecies"
cp -r dependencies/* release/

if [ $1 -eq 1 ]
then
    rm release/altspam.exe
    rm release/*.dll
else
    rm release/altspam
fi

echo "DONE. Check the release directory!"
