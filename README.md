# eclipselink-build-support

mode:
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
exact content can be controlled by `el.fwk.exclusionFilter` (default: `%regex[.*TestRunner[0-9].*]`)
  * model from classes (exclude `*.jar`, `META-INF/persistence.xml`, `META-INF/sessions.xml`)
  * tests from testClasses
  * default project resources exluding `persistence.xml`
  * resources from `${ejbConf}` (default: `${project.basedir}/src/main/resources-server`)
  * classifier: ejb

