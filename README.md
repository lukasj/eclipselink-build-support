# eclipselink-build-support

earConf (`${project.basedir}/src/main/resources-ear`) resources for EAR

ejbConf (`${project.basedir}/src/main/resources-server`) resources for EJB jar

mode:
* EAR (default)
  * `org.eclipse.persistence.core.test.framework` and `junit` are included and cannot be excluded 
  * dependencies are packaged under the `lib` folder
  * created ejb jar is placed under the root
  * `member_X` dependencies are placed under the root
  * resources from `${earConf}`
  * classifier: ear
* EJB
  * content of `org.eclipse.persistence.jpa.test.framework` is expanded under the root,
exact content can be controlled by `el.fwk.exclusionFilter` (default: `%regex[.*TestRunner[0-9].*]`)
  * model from classes (exclude `*.jar`, `META-INF/persistence.xml`)
  * tests from testClasses
  * resources from `${ejbConf}`
  * classifier: ejb

libs
   


  /**
    * Set this to <code>true</code> to bypass ear-test-jar generation.
      */
      @Parameter( property = "maven.test.skip" )
      private boolean skip;

  /**
    * Timestamp for reproducible output archive entries.
      */
      @Parameter(defaultValue = "${project.build.outputTimestamp}")
      private String outputTimestamp;

  /**
    * Name of the generated JAR.
      */
      @Parameter( defaultValue = "${project.build.finalName}", readonly = true )
      private String finalName;

  /**
    * The archive configuration to use.
      */
      @Parameter
      private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  /**
    * Artifact Ids of project dependencies to be included in the target archive.
      */
      @Parameter
      private List<String> libs;


