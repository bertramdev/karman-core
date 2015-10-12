package com.bertramlabs.plugins.karman.exceptions

class ProviderNotFoundException extends Exception {
  public ProviderNotFoundException() {
    super('The Requested Storage Provider was not found')
  } 

  public ProviderNotFoundException(String provider) {
    super("The Requested Storage Provider was not found: ${provider}")
  } 
}