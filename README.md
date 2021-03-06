# PEJava
## Puppet Enterprise Bindings for Java

# Status: API subject to change

## What's this?
Puppet Enterprise ships with a bunch of REST APIs for which you must presently 
code support for yourself.  This library provides easy access to selected APIs 
through a simple Java interface.

## Why not pe-client-tools?
Puppet provide a 
[pe-client-tools package](https://docs.puppet.com/pe/latest/install_pe_client_tools.html) 
which includes access to many APIs on the command line.  This is useful for a 
lot of situations but the requirements to install global configuration files and
RPM packages may be a roadblock to deployment for some organisations.

Additionally, shelling out to perform API calls can be somewhat slower and more 
resource intensive while opening new attack surfaces (system calls) and reducing
the richness of error messages (exit status/parsing error messages vs 
exceptions).

For native Java projects such as Atlassian plugins, it makes sense to perform 
these simple REST requests at the Java level, and to bundle this functionality
into a reusable jar.

## Functionality
This is not a complete mapping of the various Puppet APIs.  The currently
supported actions are only those required by the puppet_deploy plugin for
Bitbucket and Bamboo:

### Code Manager
* [POST `/v1/deploys`](https://puppet.com/docs/pe/2019.1/code_manager_api.html#code-mgr-post-deploys)

## Contributing
Pull requests for new functionally are very welcome.  Alternatively, please 
email sales@declarativesystems.com if you are interested in paid enhancements 
to this library.

## Puppet Enterprise version
The API is targeted at Puppet Enterprise:
* 2018.4
* 2019.1