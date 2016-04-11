package com.bertramlabs.plugins.karman.util

import groovy.transform.CompileStatic

/**
 * Created by davidestes on 4/8/16.
 */
@CompileStatic
class ChunkedInputStream extends InputStream{
	private InputStream sourceStream
	private Long chunkSize
	private Long position=0
	private boolean eof = false
	public ChunkedInputStream(InputStream sourceStream, Long chunkSize) {
		this.sourceStream = sourceStream
		this.chunkSize = chunkSize
		this.position = 0
	}

	public Boolean nextChunk() {
		if(eof) {
			return false
		}
		position=0
		return true
	}


	@Override
	public int read() throws IOException {
		if(position >= chunkSize) {
			return -1
		}
		int c = sourceStream.read()
		if(c < 0) {
			eof=true
			return c
		}
		position++

		return c
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(position >= chunkSize) {
			return -1
		}
		int intendedLen = len
		if(intendedLen + position > chunkSize) {
			intendedLen = (chunkSize - position) as int
		}
		int c = sourceStream.read(b, off, intendedLen)
		if(c < 0) {
			eof=true
			return c
		}
		position += c
//		println "Read bytes ${c} for desired len ${len}"
		return c
	}

	@Override
	public int available() {
		if(eof) {
			return -1
		}
		int a = super.available()
		if (a > (chunkSize - position)) {
			return (chunkSize - position) as int
		}
		return a
	}


	public void close() {
		super.close()
	}
}
