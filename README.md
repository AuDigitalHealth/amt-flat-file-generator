# AMT Flat File Generator
The AMT Flat File Generator demonstrates a non-relational method of working with SNOMED CT RF2 files, and traverses the AMT model. It comes as a Java library and has a command line wrapper which produces a "flat" snapshot format of AMT from a set of RF2 files.

# How to build it
The easiest way to build it is use the Maven POM file provided. Clone or download the git repository and once Maven (and prequisites like a JDK) are installed the project can be built by running

```
mvn package
```

# How to run it from the command line
Once mvn package has been run, there will be a JAR file in a new directory called target created by Maven called amt-to-flat-file-master-SNAPSHOT-jar-with-dependencies.jar (note the "master-SNAPSHOT" section of this name will change if the POM version is changed).

You can of course rename the JAR file whatever you like.

This is an executable JAR file, so the AMT Flat File Generator can be run from the command line as follows.
```
java -jar amt-to-flat-file-master-SNAPSHOT-jar-with-dependencies.jar
```

Running with no parameters will result in the following usage message
```
Parsing failed.  Reason: Missing required options: i, o
usage: Amt2FlatFile
 -e,--exit-on-error                             Flag dictating whether the program
                                                will exit on an error or keep
                                                processing
 -i,--inputFile <AMT_ZIP_FILE_PATH>             Input AMT release ZIP file
 -j,--junitFile <JUNIT_FILE_PATH>               Output file path to write out the
                                                junit result file
 -o,--outputFile <OUTPUT_FILE>                  Output file path to write out the
                                                flat file
 -r,--replacementsOutputFile <REPLACEMENT_FILE> Putput path to write out the replacement
                                                file for inactive concepts.
```

The command line parameters are explained in the table below

Switch | Short alias | Parameter | Optional | Default | Description
------ | ----------- | --------- | -------- | ------- | -----------
-i | --inputFile | Path to AMT RF2 zip file | No | N/A | Specifies the location of the input RF2 zip file containing AMT, note it must contain a Snapshot RF2 release, not a Full or Delta release
-o | --outputFile | Path to write the resultant AMT flat file to | No | N/A | Specifies the location to write out the resultant calculated AMT flat file to. If the path does not exist an attempt will be made to create it. If a file already exists at this location it will be overwritten.
-e | --exit-on-error | N/A | Yes | False | If set, if an error is encountered transforming the specified RF2 file to the AMT flat file processing will halt immediately. If not set (default) processing will continue and all encountered errors will be reported
-j | --junitFile | Path to write out errors as a JUnit file | Yes | N/A | Specifying this option will cause any errors encountered transforming the RF2 data to an AMT flat file to be written into a JUnit XML test resut file. This is particularly useful if this utility is being used by a continuous integration server capable of reporting tests from JUnit test results.
-r | --replacementsOutputFile | Path to write out replacements for inactive concepts | Yes | N/A | If set, a CSV file containing rows for inactive concepts and their replacements will be produced. Note there can be more than one replacement for an inactive concept depending upon the reason it was inactivated, **assuming one for one replacement is NOT SAFE**.

An example of executing the utility is below
```
java -jar target/amt-to-flat-file-master-SNAPSHOT-jar-with-dependencies.jar -i NCTS_SCT_RF2_DISTRIBUTION_32506021000036107-XXX-SNAPSHOT.zip -o amt-flat-file.csv
```
# How to run it as a Maven Mojo
The Maven project also creates a Maven Mojo for inclusion in a Maven build.

Properties mirror the command line list above, and are

Property name | Required | Default
------------- | -------- | -------
inputZipFilePath | Yes | None
outputFilePath | Yes | None
junitFilePath | No | target/ValidationErrors.xml
exitOnError | No | false
replacementsOutputFile | No | None

An example execution is
```xml
<build>
  <plugins>
    <plugin>
      <groupId>au.gov.digitalhealth.terminology</groupId>
      <artifactId>amt-to-flat-file</artifactId>
      <configuration>
        <inputZipFilePath>NCTS_SCT_RF2_DISTRIBUTION_32506021000036107-XXX-SNAPSHOT.zip</inputZipFilePath>
        <outputFilePath>amt-flat-file.csv</outputFilePath>
      </configuration>
    <plugin>
  <plugins>
<build>
```

# What it produces
The AMT Flat File Generator creates a "snapshot" Comma Separated Values (CSV) file containing an extract of the AMT data from the SNOMED CT-AU release it was produced from. The file contains the following columns
* CTPP ID
* CTPP preferred term
* ARTGID
* TPP ID
* TPP preferred term
* TPUU ID
* TPUU preferred term
* TPP TP ID
* TPP TP preferred term
* TPUU TP ID
* TPUU TP preferred term
* MPP ID
* MPP preferred term
* MPUU ID
* MPUU preferred term
* MP ID
* MP preferred term

It is a "snapshot" in the sense that it contains rows representing the state of all the active AMT concepts in the snapshot RF2 files it was created from. It contains no history of AMT content leading up to that point and no timestamps - it is a point in time snapshot.

Due to the one to many relationships between some of the AMT concepts and identifiers for a product, the file contains one or more row for each active CTPP in the SNOMED CT-AU release it was generated from. Inactive AMT concepts are not be present in the file.

Each row represents a set of related AMT concepts expressed across the columns. Only rows representing the most proximal concept from each AMT concept class (each column) is included, rows for redundant super-types are suppressed.

Optionally it produces a replacement mapping file for inactive concepts in AMT from the Historical Association Reference Sets.

**NOTE: this file can contain more than one row for an inactive concept which indicates it is replaced by more than one concept.** This means a decision needs to be made as to which replacement to use.

The file has the following columns
* Inactive concept ID
* Inactive concept preferred term
* Replacement type ID (historical association types explained at https://confluence.ihtsdotools.org/display/DOCTSG/4.2.3+Historical+Association+Reference+Sets)
* Replacement type preferred term
* Replacement concept ID
* Replacement concept preferred term
