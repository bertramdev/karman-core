package com.bertramlabs.plugins.karman.util

public class Mimetypes {

    static String MIMETYPE_OCTET_STREAM = 'application/octet-stream'

    private Map mimeTypes = [:]


    public static Mimetypes getInstance() {
        return new Mimetypes()
    }

    String getMimetype(String fileName) {
        String mimetype = MIMETYPE_OCTET_STREAM // Default
        if (!mimeTypes) {
            loadMimetype()
        }

        String extension = fileName.tokenize('.').last()
        if (extension && mimeTypes[extension]) {
            mimetype = mimeTypes[extension]
        }
        mimetype
    }

    String getMimetype(File file) {
        getMimetype(file.name)
    }

    private loadMimetype() {
        getClass().getResourceAsStream('/META-INF/karman/mime.types').eachLine {
            String line = it.trim()
            if (line && !line.startsWith('#')) { // Ignore comments and empty lines.
                StringTokenizer st = new StringTokenizer(line, " \t")
                if (st.countTokens() > 1) {
                    String mimetype = st.nextToken()
                    while (st.hasMoreTokens()) {
                        String extension = st.nextToken()
                        mimeTypes[extension] = mimetype
                    }
                }
            }
        }
    }

}