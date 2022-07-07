# eclipselink-build-support

## eclipselink-testbuild-plugin

A maven plugin packaging eclipselink test application(s) for the deployment on the application
server. It produces EJB jar and/or EAR application from the project structure with the content
as is outlined bellow and attaches built artifacts to the project.

mode (property: `el.packager.mode`):
* EAR (default)
  * `org.eclipse.persistence.core.test.framework` included by default
  * `junit` included by default
  * `test-jar` dependencies under the `lib` folder
  * created ejb jar is placed under the root
  * `member_X` dependencies are placed under the root
  * resources from `${earConf}` (default: `${project.basedir}/src/main/resources-ear`)
  * classifier: ear
* EJB
  * content of `org.eclipse.persistence.jpa.test.framework` is expanded under the root,
exact content can be controlled by `el.packager.fwk.exclusionFilter` (default: `%regex[.*TestRunner[0-9].*]`)
  * model from classes (exclude `*.jar`, `META-INF/persistence.xml`, `META-INF/sessions.xml`)
  * tests from testClasses
  * default project resources excluding `persistence.xml`
  * resources from `${ejbConf}` (default: `${project.basedir}/src/main/resources-ejb`)
  * classifier: ejb

