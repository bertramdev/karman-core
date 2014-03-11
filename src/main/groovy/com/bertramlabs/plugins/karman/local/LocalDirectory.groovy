/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.local
import com.bertramlabs.plugins.karman.*
import org.apache.tools.ant.DirectoryScanner

class LocalDirectory extends com.bertramlabs.plugins.karman.Directory {
	String region

	File getFsFile() {
		new File(provider.basePath,name)
	}
	

	Boolean exists() {
		fsFile.exists()
	}

	List listFiles(options=[:]) {
		DirectoryScanner scanner = new DirectoryScanner()
		if(options.excludes) {
			scanner.setExcludes(options.excludes as String[])
		}
		if(options.includes) {
			scanner.setIncludes(options.includes as String[])	
		}
		if(options.prefix) {
			def prefix = options.prefix
			if(prefix.endsWith("/")) {
				scanner.setIncludes([prefix + '**/*'] as String[])		
			} else {
				scanner.setIncludes([prefix+'*',prefix + '*/**/*'] as String[])		
			}
		}

		scanner.setBasedir(fsFile.canonicalPath)
		scanner.setCaseSensitive(false)
		scanner.scan()

		scanner.getIncludedFiles().flatten().collect {
			new LocalCloudFile(provider: provider, parent: this, name: it)
		}
	}

	def save() {
		fsFile.mkdirs()
	}

	CloudFile getFile(String name) {
		new LocalCloudFile(provider: provider, parent: this, name: name)
	}

}