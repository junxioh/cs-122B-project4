#!/bin/bash

# 设置日志文件
LOG_FILE="movie_import.log"
echo "Starting import at $(date)" > $LOG_FILE

# 设置 XML 文件路径
MAINS_XML="src/main/resources/xml/stanford-movies/mains243.xml"
CASTS_XML="src/main/resources/xml/stanford-movies/casts124.xml"

# 检查文件是否存在
if [ ! -f "$MAINS_XML" ]; then
    echo "Error: $MAINS_XML not found!"
    exit 1
fi

if [ ! -f "$CASTS_XML" ]; then
    echo "Error: $CASTS_XML not found!"
    exit 1
fi

# 编译项目
echo "Compiling project..." | tee -a $LOG_FILE
mvn clean compile >> $LOG_FILE 2>&1

if [ $? -ne 0 ]; then
    echo "Compilation failed! Check $LOG_FILE for details."
    exit 1
fi

# 运行导入程序
echo "Running import program..." | tee -a $LOG_FILE
echo "Using XML files:" | tee -a $LOG_FILE
echo "  Mains: $MAINS_XML" | tee -a $LOG_FILE
echo "  Casts: $CASTS_XML" | tee -a $LOG_FILE

mvn exec:java -Dexec.mainClass="main.java.com.fabflix.MovieDataImporter" \
    -Dexec.args="$MAINS_XML $CASTS_XML" >> $LOG_FILE 2>&1

if [ $? -ne 0 ]; then
    echo "Import failed! Check $LOG_FILE for details."
    exit 1
fi

echo "Import completed successfully!" | tee -a $LOG_FILE
echo "Check $LOG_FILE for detailed logs." 