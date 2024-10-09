# Karman Differential Storage

Karman differential storage provider is a wrapper around other karman storage providers for storing
incremental backup data based on a source file. It is capable of chaining source files together so only the 
difference between the previous file is stored. This is useful for storing incremental backups of large files.
Another feature of this model is that EACH incremental copy contains the block table of the entire backup in a file `karman.diff`. 
This allows for very fast restore of a long incremental backup chain.

Each differential backup file is a directory with a manifest and block files
chunked in 1MB source blocks. The manifest (`karman.diff`) is used to assemble the blocks into the original file.

Each block is stored in a directory based on its block number. The block directory is the left 52 bits of a long in hexadecimal string format zerofill to 2 characters minimum (i.e. `00/`).
This is done to minimize file system overhead for having only so many files within each directory. All blocks are stored with a GZIP compression


The manifest (`karman.diff`) file format contains a header and block lines for minimum size use. Some operations may need to load a large chunk of this table into memory. The format is structured similar to an HTTP Response. The top section is a list of properties with the format of a key and value separated by a `:`(colon).
Block data then immediately follows the header keys when a blank line is detected.

Example `karman.diff` file format:
```
fileName: <source file name>
fileSize: <source file size>
blockSize: <block size>
version: <version>
files:<comma list of file names in index order>

<block number (8byte)><block size (4byte)><fileIndex (0 if current file) (4byte)><block hash SHA3-224 precompressed (22bytes)>
<block number (8byte)><block size (4byte)><fileIndex (0 if current file) (4byte)><block hash SHA3-224 precompressed (22bytes)>
<block number (8byte)><block size (4byte)><fileIndex (0 if current file) (4byte)><block hash SHA3-224 precompressed (22bytes)>
```

The header contains the following keys:
* fileName: The name of the source file
* fileSize: The size of the source file
* blockSize: The size of each block
* version: The version of the differential storage format
* files: A comma separated list of file names in the index order

The files list is used to reference where the block data is stored when the file is an incremental. The index starts at 1 (0 is reserved for meaning current file).
This file index should be used to look at the files key in the header to determine where to look. If the file cannot be found, simply step down the list until a file is found. 
This is designed such that a file flatten does not require a full rebuild of every incremental file in the tree.

Each block contains 4 fields:

* Block Number: 8 bytes (long)
* Block Size: 4 bytes (int)
* File Index: 4 bytes (int)
* Block Hash: 22 bytes SHA3-224 hash digest of the contents of the block before compression.


## Future Work

The model was designed to allow for some additional functionality by supporting a dynamic list of header keys. The `version` property could therefore be used to let the `DiferentialCloudFile` know how to decode the file.
Some additional features that could be added are:

* Encryption: The block data could be encrypted with a key salt stored in the header.
* Compression: The block data could be compressed with a different algorithm.


## Usage

Use just like any other karman storage provider. The only difference is that the creation of this provider takes a sourceProvider (this could be local, amazon s3, azure, google, nfs, cifs, etc.)

```groovy
import com.bertramlabs.plugins.karman.differential.DifferentialStorageProvider
import com.bertramlabs.plugins.karman.StorageProvider

def sourceProvider = StorageProvider.create(provider:'local', path: '/home/destes/path')
def targetProvider = StorageProvider.create(provider:'local', path: '/home/backups')

DifferentialStorageProvider differentialProvider = new DifferentialStorageProvider(sourceProvider: targetProvider)

//store a file
def savePath = differentialProvider['.']['mydisk.qcow2']
def sourcePath = sourceProvider['images/vms']['mydisk.qcow2']

savePath.setContentLength(sourcePath.getContentLength())
savePath.setInputStream(sourcePath.getInputStream())
savePath.save()
```

The above example simply saves a file in the incremental file format. 

If you reference a base file than it can be chained with the `setLinkedFile` method.

```groovy
import com.bertramlabs.plugins.karman.differential.DifferentialStorageProvider
import com.bertramlabs.plugins.karman.StorageProvider

def sourceProvider = StorageProvider.create(provider:'local', path: '/home/destes/path')
def targetProvider = StorageProvider.create(provider:'local', path: '/home/backups')

DifferentialStorageProvider differentialProvider = new DifferentialStorageProvider(sourceProvider: targetProvider)

//store a file
def savePath = differentialProvider['.']['mydisk.qcow2']
def previousBackup = differentialProvider['.']['mydisk_old.qcow2']
def sourcePath = sourceProvider['images/vms']['mydisk.qcow2']

savePath.setLinkedFile(previousBackup) //this is the important part
savePath.setContentLength(sourcePath.getContentLength())
savePath.setInputStream(sourcePath.getInputStream())
savePath.save()
```

If you want to delete the base file and decouple a stored file from the incremental source. then the `flatten` command can be used:

```groovy
def savePath = differentialProvider['.']['mydisk.qcow2']
savePath.flatten()
```

This will modify the storage of the file and decouple it from any base files so it can be fully independent.

Restoring a file to an original format is also quite simple:

```groovy
import com.bertramlabs.plugins.karman.differential.DifferentialStorageProvider
import com.bertramlabs.plugins.karman.StorageProvider

def sourceProvider = StorageProvider.create(provider:'local', path: '/home/destes/path')
def targetProvider = StorageProvider.create(provider:'local', path: '/home/backups')

DifferentialStorageProvider differentialProvider = new DifferentialStorageProvider(sourceProvider: targetProvider)

//store a file
def backupFile = differentialProvider['.']['mydisk.qcow2']
def recoveryPath = sourceProvider['images/restore']['mydisk.qcow2']

recoveryPath.setContentLength(backupFile.getContentLength())
recoveryPath.setInputStream(backupFile.getInputStream())
recoveryPath.save()
```
