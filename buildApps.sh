#!/bin/bash
PSHDL="/Users/karstenbecker/Dropbox/PSHDL"
version=`cat $PSHDL/version`
sversion="${version:1:${#version}}"
ROT=`tput setaf 1`;
GRUEN=`tput setaf 2`;
NORMAL=`tput sgr0`;

cd $PSHDL/org.pshdl.localhelper

for platform in linux32 linux64 macosx64 win32 win64; do
	echo -n "Building $platform package: "
	if mvn -Dpshdl.root=/Users/karstenbecker/Dropbox/PSHDL -P $platform clean package; then
		echo "${GRUEN}done${NORMAL}";
		cp $PSHDL/org.pshdl.localhelper/target/$platform/localhelper-*.jar $PSHDL/wiki.localhelper/localhelper-$platform.jar
	else
		echo "${ROT}failed${NORMAL}";
		exit 1;
	fi
done

cd $PSHDL/org.pshdl.localhelper
echo -n "Code signing fpga_programmer with JRE: "
if codesign -s "Karsten Becker" target/macosx64/PSHDLLocalHelperJRE.app/Contents/MacOS/fpga_programmer; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi

echo -n "Code signing JRE: "
if codesign -s "Karsten Becker" target/macosx64/PSHDLLocalHelperJRE.app/Contents/PlugIns/jdk*.jdk; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi

echo -n "Code signing Mac OS App with JRE: "
if codesign -s "Karsten Becker" target/macosx64/PSHDLLocalHelperJRE.app; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi
echo "Zipping up and moving to wiki"
cd target/macosx64
tar -cjf PSHDLLocalHelperMacJRE.app.tar.bz2 PSHDLLocalHelperJRE.app
mv PSHDLLocalHelperMacJRE.app.tar.bz2 $PSHDL/wiki.localhelper/PSHDLLocalHelperMacJRE.tar.bz2

cd $PSHDL/org.pshdl.localhelper
echo -n "Code signing fpga_programmer: "
if codesign -s "Karsten Becker" target/macosx64/PSHDLLocalHelper.app/Contents/MacOS/fpga_programmer; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi

echo -n "Code signing Mac OS App: "
if codesign -s "Karsten Becker" target/macosx64/PSHDLLocalHelper.app; then
	echo "${GRUEN}done${NORMAL}";
else
	echo "${ROT}failed${NORMAL}";
	exit 1;
fi
echo "Zipping up and moving to wiki"
cd target/macosx64
tar -cjf PSHDLLocalHelperMacNoJRE.app.tar.bz2 PSHDLLocalHelper.app
mv PSHDLLocalHelperMacNoJRE.app.tar.bz2 $PSHDL/wiki.localhelper/PSHDLLocalHelperMacNoJRE.tar.bz2

cd $PSHDL/wiki.localhelper
echo -n "Adding app to git commit:   "
if git add *.tar.bz2 *.jar; then
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