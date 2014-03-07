#!/bin/bash
PSHDL="/Users/karstenbecker/Dropbox/PSHDL"
version=`cat $PSHDL/version`
sversion="${version:1:${#version}}"
ROT=`tput setaf 1`;
GRUEN=`tput setaf 2`;
NORMAL=`tput sgr0`;


echo -n "Building macosx64 package: "
if mvn -P macosx64 package; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi
echo -n "Code signing fpga_programmer "
if codesign -s "Karsten Becker" target/PSHDLLocalHelper.app/Contents/MacOS/fpga_programmer; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi

echo -n "Code signing Mac OS App "
if codesign -s "Karsten Becker" target/PSHDLLocalHelper.app; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi
echo "Zipping up and moving to wiki"
cd target
tar -cjf PSHDLLocalHelperMacNoJRE.app.tar.bz2 PSHDLLocalHelper.app
mv PSHDLLocalHelperMacNoJRE.app.tar.bz2 $PSHDL/wiki.localhelper/PSHDLLocalHelperMacNoJRE.tar.bz2

cd $PSHDL/wiki.localhelper
echo -n "Adding app to git commit:   "
if git add PSHDLLocalHelperMacNoJRE.tar.bz2; then
    echo "${GRUEN}done${NORMAL}";
else
    echo "${ROT}failed${NORMAL}";
    exit 1;
fi

echo -n "Commiting localhelper wiki: "
if git commit --quiet -m "Updated localhelper.jar to version $version"; then
    echo "${GRUEN}done${NORMAL}";
else
    echo "${ROT}failed${NORMAL}";
    exit 1;
fi

echo -n "Tagging localhelper line wiki:  "
if git tag -a $version -m "Version $version"; then
    echo "${GRUEN}done${NORMAL}";
else
    echo "${ROT}failed${NORMAL}";
    exit 1;
fi

echo -n "Pushing localhelper line wiki:  "
if git push --quiet --tags bitbucket master:master; then
    echo "${GRUEN}done${NORMAL}";
else
    echo "${ROT}failed${NORMAL}";
    exit 1;
fi