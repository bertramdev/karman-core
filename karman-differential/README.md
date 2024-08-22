# Karman Differential Storage

Karman differential storage provider is a wrapper around other karman storage providers for storing
differential backup data based on a source file

Each differential backup file is a directory with a manifest and block files
chunked in 1MB source blocks. The manifest is used to assemble the blocks into the original file.

The manifest file format contains a header and block lines for minimum size
as this manifest typically will need fully loaded into memory to process.

```
header: <source file size>
blockSize: <block size>
files:<comma list of file names in index order>
BLOCKS
<block number><fileIndex 0 if current file 4byte><block size 4byte><block hash sha-256 precompressed>:<block metadata i.e. encryption future use>
```

Files are stored in hash directories to minimize file overhead within a directory
Block number directory names are the hex representation of the block number




