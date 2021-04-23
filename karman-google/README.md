Karman Google Groovy
=================

Karman Google is a Google Cloud Storage implementation of the Karman Cloud Service / Storage Interface. It allows one to interact with Google Cloud Storage via the standard Karman API interfaces


Usage / Documentation
---------------------

To instantiate a Google Cloud Storage provider simply do:

```groovy
import com.bertramlabs.plugins.karman.*


provider = StorageProvider.create(
    provider: 'google',
    clientEmail: CLIENT_EMAIL,
    privateKey: PRIVATE_KEY,
    projectId: PROJECT_ID
)

//example getting file contents
def file = provider['mybucket']['example.txt']
return file.text
```

Check the Karman API Documentation for details on how to interace with cloud files:

http://bertramdev.github.io/karman


Contributions
-------------
All contributions are of course welcome as this is an ACTIVE project. Any help with regards to reviewing platform compatibility, adding more tests, and general cleanup is most welcome.
Thanks to several people for suggestions throughout development.