#!/bin/bash
mvn -P macosx64 package
codesign -s "Karsten Becker" target/PSHDLLocalHelper.app/Contents/MacOS/fpga_programmer
codesign -s "Karsten Becker" target/PSHDLLocalHelper.app
tar -cjf PSHDLLocalHelperMacNoJRE.app.tar.bz2 target/PSHDLLocalHelper.app
mv PSHDLLocalHelperMacNoJRE.app.tar.bz2 ~/Dropbox/PSHDL/wiki.localhelper/PSHDLLocalHelperMacNoJRE.tar.bz2