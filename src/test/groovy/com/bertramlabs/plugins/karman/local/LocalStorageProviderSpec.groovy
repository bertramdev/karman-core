package com.bertramlabs.plugins.karman.local

import com.bertramlabs.plugins.karman.KarmanConfigHolder
import com.bertramlabs.plugins.karman.StorageProvider
import spock.lang.Specification

/**
 * TODO: write doc
 */
class LocalStorageProviderSpec extends Specification {

    def "it can be created"() {
        given:
        StorageProvider.registerProvider(LocalStorageProvider)

        when:
        StorageProvider storageProvider = StorageProvider.create(provider: 'local', basePath: '.')

        then:
        storageProvider
    }
}