# README #

A plugin/module for the NetBeans IDE to directly open OpenJDK sources.

__Note__: This plugin is obsolette, as the support for OpenJDK sources has been merged directly into Apache NetBeans.

### Installation ###

Go to Tools/Plugins, click on the Settings tab, and Add `http://lahoda.info/hudson/job/nb-jdk-project/lastSuccessfulBuild/artifact/build/updates/updates.xml` as a new plugin center. From this plugin center, install *JDK Project for NetBeans* and *JTReg Support*.

### Basic Usage ###

For repositories using the Modular Source Code layout, to open individual modules should be opened as projects. Tests of the `jdk` repository are stashed under the `java.base` module.

For repositories not using the Modular Source Code layout, open the `jdk` repository as a project.

### Caveats/Limitations ###

Current known caveats/limitations:

* there must exist a configuration when opening the projects, and when starting the IDE with the projects open. If there is more than one configurations, one is chosen. 
* the project should contain the platform independent sources and the sources for the current platform. Sources for the other plaforms are not supported at this time.
* this project is incompatible with existing legacy project metadata in jdk/make/netbeans, and for modular build also with metadata in langtools/make/netbeans.
* only NetBeans 8.0 and newer are supported.
* the projects currently don't support building, testing or any other similar actions.
