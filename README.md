# eclipselink-build-support

## eclipselink-testbuild-plugin

A maven plugin packaging eclipselink test application(s) for the deployment on the application
server. It produces EJB jar and/or EAR application from the project structure with the content
as is outlined bellow and attaches built artifacts to the project.

mode (property: `el.packager.mode`):
* EAR (default)
  * `org.eclipse.persistence.core.test.framework` included by default
  * `junit` included by default
  * created ejb jar is placed under the root
  * `member_X` dependencies are placed under the root
  * resources from `${earConf}` (default: `${project.basedir}/src/main/resources-ear`)
  * classifier: ear
* EJB
  * content of `org.eclipse.persistence.jpa.test.framework` is expanded under the root,
exact content can be controlled by `el.packager.fwk.exclusionFilter` (default: `%regex[.*TestRunner[0-9].*]`)
  * `model` (classifier)/`test-jar` (type) dependencies are expanded under the root
  * model from classes (exclude `*.jar`, `META-INF/persistence.xml`, `META-INF/sessions.xml`)
  * tests from testClasses
  * default project resources excluding `persistence.xml`
  * resources from `${ejbConf}` (default: `${project.basedir}/src/main/resources-ejb`)
  * if `persistence.xml` and/or `ejb-jar.xml` descriptors are not found under `${ejbConf}` directory,
    they are generated from the project's default `persistence.xml` found under project's default resources;
    `el.packager.descriptors` property can be set to `false` to explicitly disable generation of these descriptors
  * classifier: ejb

