var documents = [

{
    "id": 0,
    "uri": "user-guide/index.html",
    "menu": "user-guide",
    "title": "Overview",
    "text": " Table of Contents Overview Features Overview This library is an opinionated library that provides a basis for backend applications written in Scala 3 using the TypeLevel stack. It is a work in progress and is not ready for production use. Features {project-name} provides several core features used in backend applications: API server Admin server Configuration Logging Health checks OpenTelemetry-based observability It also provides several optional features: Database access HTTP client Feature flags "
},

{
    "id": 1,
    "uri": "user-guide/10_quick-start.html",
    "menu": "user-guide",
    "title": "Quick Start",
    "text": " Table of Contents Quick Start Installation Usage Application Metadata Quick Start This documentation needs to be written. You can help us by contributing to the documentation . Installation This library is currently available for Scala binary version 3.3.1. To use the latest version, include the following in your build.sbt : libraryDependencies ++= Seq( \"com.rlemaitre\" %% \"pillars-core\" % \"{project-version}\" ) You can also add optional modules to your dependencies: libraryDependencies ++= Seq( \"com.rlemaitre\" %% \"pillars-db\" % \"{project-version}\", \"com.rlemaitre\" %% \"pillars-flags\" % \"{project-version}\", \"com.rlemaitre\" %% \"pillars-http-client\" % \"{project-version}\" ) Usage You can find an example project in the modules/example directory. First, you need to create a configuration file . You can find an example in the modules/example/src/main/resources/application.conf file. Then, you can create your entry point by extending the EntryPoint trait: object app extends pillars.EntryPoint: // (1) def app: pillars.App[IO] = new pillars.App[IO]: // (2) def infos: AppInfo = BuildInfo.toAppInfo // (3) def run(using p: Pillars[IO]): IO[Unit] = // (4) import p.* for _ &lt;- logger.info(s\"📚 Welcome to ${config.name}!\") _ &lt;- flag\"feature-1\".whenEnabled: DB[IO].use: session =&gt; for date &lt;- session.unique(sql\"select now()\".query(timestamptz)) _ &lt;- logger.info(s\"The current date is $date.\") yield () _ &lt;- HttpClient[IO].get(\"https://pillars.rlemaitre.com\"): response =&gt; logger.info(s\"Response: ${response.status}\") _ &lt;- apiServer.start(endpoints.all) yield () end for end run end app 1 The EntryPoint trait is a simple trait that provides a main method and initialize the Pillars instance. 2 The pillars.App[IO] must contain your application logic 3 infos defines some metadata about your application. It is used by the admin server to display information about your application. See 4 The run is the entry point of your application. Here, you have access to the Pillars instance. Then, you can run your application. For example, you can run it with sbt : sbt \"example/run\" The log should display something like: 2024.01.21 22:36:19:0000 [io-comp...] [INFO ] pillars.Pillars.apply:52 - Loading modules... 2024.01.21 22:36:19:0001 [io-comp...] [INFO ] pillars.Pillars.loadModules:87 - Found 2 module loaders: db, feature-flags 2024.01.21 22:36:19:0002 [io-comp...] [INFO ] pillars.db.db.load:57 - Loading DB module 2024.01.21 22:36:19:0003 [io-comp...] [INFO ] pillars.db.db.load:68 - DB module loaded 2024.01.21 22:36:19:0004 [io-comp...] [INFO ] pillars.flags.FlagManager.load:54 - Loading Feature flags module 2024.01.21 22:36:19:0005 [io-comp...] [INFO ] pillars.flags.FlagManager.load:57 - Feature flags module loaded 2024.01.21 22:36:19:0000 [io-comp...] [INFO ] pillars.AdminServer.start:22 - Starting admin server on 0.0.0.0:19876 2024.01.21 22:36:19:0006 [io-comp...] [INFO ] example.app.run:24 - 📚 Welcome to Bookstore! 2024.01.21 22:36:19:0000 [io-comp...] [INFO ] example.app.run:29 - The current date is 2024-01-21T22:36:19.695572+01:00. 2024.01.21 22:36:19:0000 [io-comp...] [INFO ] pillars.ApiServer.init:21 - Starting API server on 0.0.0.0:9876 2024.01.21 22:36:19:0001 [io-comp...] [INFO ] org.http4s.netty.server.NettyServerBuilder - Using NIO EventLoopGroup 2024.01.21 22:36:19:0001 [io-comp...] [INFO ] org.http4s.netty.server.NettyServerBuilder - Using NIO EventLoopGroup 2024.01.21 22:36:19:0002 [io-comp...] [INFO ] org.http4s.netty.server.NettyServerBuilder - Started Http4s Netty Server at http://[::]:9876/ 2024.01.21 22:36:19:0002 [io-comp...] [INFO ] org.http4s.netty.server.NettyServerBuilder - Started Http4s Netty Server at http://[::]:19876/ You can now access the API at http://localhost:9876 and the admin server at http://localhost:19876 . For example, to get the readiness porbe status, you can run: $ curl http://localhost:19876/admin/probes/health | jq { \"status\": \"pass\", \"checks\": [ { \"componentId\": \"db\", \"componentType\": \"datastore\", \"status\": \"pass\" } ] } Application Metadata The infos property of the App[F] trait defines some metadata about your application. You have two ways of defining it: You can directly create an instance of AppInfo : val infos = AppInfo( name = App.Name(\"Bookstore\"), version = App.Version(\"1.0.0\"), description = App.Description(\"A simple bookstore\") ) Or, if you are using the sbt-buildinfo plugin, you can use the BuildInfo object. In your build.sbt , add the following lines to your project definition: lazy val example = Project(\"pillars-example\", file(\"modules/example\")) .enablePlugins(BuildInfoPlugin) // (1) .settings( name := \"pillars-example\", // (2) description := \"pillars-example is an example of application using pillars\", // (3) libraryDependencies ++= Dependencies.tests, buildInfoKeys := Seq[BuildInfoKey](name, version, description), // (4) buildInfoOptions := Seq(BuildInfoOption.Traits(\"pillars.BuildInfo\")), // (5) buildInfoPackage := \"example.build\", // (6) publish / skip := true ) .dependsOn(core, db, flags, httpClient) 1 Enable the BuildInfo plugin 2 Define the name of your application 3 Define the description of your application 4 Tell buildinfo to generate the BuildInfo object including at least name , description and version properties. In this specific case, version is defined by the sbt-dynver plugin. 5 Configure BuildInfo to implement the pillars.BuildInfo trait. It is required to use the BuildInfo object in your application. 6 Specify in which package will be generated the BuildInfo object. Then, you can use the BuildInfo object in your application: import example.build.BuildInfo val app = new App[IO]: override val infos = BuildInfo.toAppInfo override def run(pillars: Pillars[IO]): IO[Unit] = ??? "
},

{
    "id": 2,
    "uri": "user-guide/30_modules/index.html",
    "menu": "user-guide",
    "title": "Optional Modules",
    "text": " Table of Contents Modules Database HTTP Client Feature Flags Write your own module Modules Pillars includes several optional modules: Database HTTP Client Feature Flags Database The database module provides a simple abstraction over the database access layer. It is based on the skunk library and provides a simple interface to execute queries and transactions. Read more HTTP Client The HTTP Client module provides a simple abstraction over the HTTP client layer. It is based on the http4s library using Netty and provides a simple interface to execute HTTP requests. Read more Feature Flags The Feature Flags module provides a simple abstraction over the feature flags layer. Read more Write your own module You can easily write your own module by implementing the Module trait. Read more "
},

{
    "id": 3,
    "uri": "user-guide/30_modules/30_flags.html",
    "menu": "user-guide",
    "title": "Feature Flags module",
    "text": " Table of Contents Feature Flags module Creating a feature flag Using a feature flag Endpoints Feature Flags module Feature flags are a way to enable or disable features in your application. They are useful for many reasons, including: Allowing you to test features in production before releasing them to all users. Allowing you to do a gradual rollout of a feature to a percentage of users. Currently, feature flags are only read from the configuration file and cannot be changed at runtime. This means that you will need to restart your application to change the value of a feature flag. In the future, we plan to add support for changing feature flags at runtime and storing them in a database. Creating a feature flag Feature flags are defined in the feature-flags section of the configuration file. feature-flags: enabled: true # (1) flags: - name: feature-1 # (2) status: enabled # (3) - name: feature-2 status: disabled 1 Whether feature flags are enabled or not. If this is set to false , all feature flags will be disabled. 2 The name of the feature flag. 3 The status of the feature flag. Possible values are enabled and disabled . Using a feature flag Feature flags can be used in your application by using the flags module on Pillars . import pillars.flags.* // (1) val flag = flag\"feature-1\" // (2) for enabled &lt;- pillars.flags.isEnabled(flag) // (3) _ &lt;- IO.whenA(enabled)(IO.println(\"Feature 1 is enabled\")) // (4) // or _ &lt;- pillars.whenEnabled(flag\"feature-2\")(IO.println(\"Feature 2 is enabled\")) // (5) // or _ &lt;- flag\"feature-3\".whenEnabled(IO.println(\"Feature 3 is enabled\")) // (6) yield () 1 Import the flags module to enable the flag string interpolator and the flags property on Pillars . 2 Create a Flag instance by using the flag string interpolator. 3 Check if the feature flag is enabled. 4 If the feature flag is enabled, perform the action you want. 5 Use the pillars.whenEnabled method to perform an action if the feature flag is enabled. 6 Use the whenEnabled method on the FeatureFlag.Name instance to perform an action if the feature flag is enabled. Endpoints Feature flags are exposed on the admin server . The defined endpoints are: GET /flags - Get all feature flags. GET /flags/+{name}+ - Get a specific feature flag. Feature flags are returned in the following format: { \"name\": \"feature-1\", \"status\": \"enabled\" } "
},

{
    "id": 4,
    "uri": "user-guide/30_modules/20_http-client.html",
    "menu": "user-guide",
    "title": "HTTP Client Module",
    "text": " Table of Contents HTTP Client module HTTP Client Configuration Using the HttpClient Module HTTP Operations HTTP Client module The HttpClient module provides HTTP client functionality for the Pillars application. It uses the http4s library for creating HTTP requests and handling HTTP responses. HTTP Client Configuration The HTTP client configuration is defined in the Config case class. It includes the following field: followRedirect : A flag indicating whether to follow redirects. The configuration is read from the application&#8217;s configuration file under the http-client section. Using the HttpClient Module To use the HttpClient module, you need to import it and then access it through the Pillars instance: import pillars.httpclient.* val httpClientModule = pillarsInstance.httpClient You can also use directly Client[F] You can then use the httpClientModule to perform HTTP operations. HTTP Operations The HttpClient module provides methods for sending HTTP requests and receiving HTTP responses. You can use the httpClient extension method on Pillars to get an instance of Client[F] : import org.http4s.client.Client val client: Client[F] = pillars.httpClient This Client[F] instance can be used to send HTTP requests by using the same methods as org.http4s.client.Client[F] . "
},

{
    "id": 5,
    "uri": "user-guide/30_modules/10_db.html",
    "menu": "user-guide",
    "title": "Database Module",
    "text": " Table of Contents Database module Database Configuration Using the DB Module Probe Database module The DB module provides database connectivity and operations for the Pillars application. It uses the Skunk library for interacting with PostgreSQL databases. Database Configuration The database configuration is defined in the DatabaseConfig case class. It includes the following fields: host : The host of the database. port : The port of the database. database : The name of the database. username : The username for the database. password : The password for the database. poolSize : The size of the connection pool. debug : A flag indicating whether to enable debug mode. probe : The configuration for the database probe. The configuration is read from the application&#8217;s configuration file under the db section. Using the DB Module To use the DB module, you need to import it and then access it through the Pillars instance: import pillars.db.* val dbModule = pillarsInstance.db You can then use the dbModule to perform database operations. You can also use directly DB[F] to perform database operations: import pillars.db.* import skunk.* def foo[F[_]](using Pillars[F]) = DB[F].use: session =&gt; session.unique(sql\"SELECT 1\".query[Int]) Probe The DB module provides a probe for health checks. val isHealthy: F[Boolean] = dbModule.probes.head.check This will return a boolean indicating whether the database is healthy or not. "
},

{
    "id": 6,
    "uri": "user-guide/30_modules/100_write-your-own-module.html",
    "menu": "user-guide",
    "title": "Write your own module",
    "text": " Table of Contents Write your own module Write your own module This documentation needs to be written. You can help us by contributing to the documentation . "
},

{
    "id": 7,
    "uri": "user-guide/20_features/20_logging.html",
    "menu": "user-guide",
    "title": "Logging",
    "text": " Table of Contents Logging Configuration Logging in your code Logging Logging is a very important part of any application. It allows you to see what is happening in your application and to debug it. Pillars uses the scribe library for logging. Configuration The logging configuration is described in the Configuration section. Logging in your code To log something in your code, you can use the logger defined on the Pillars instance. def run(using p: Pillars[IO]): IO[Unit] = import p.* for _ &lt;- logger.info(s\"📚 Welcome to ${config.name}!\") _ &lt;- logger.debug(s\"📚 The configuration is: $config\") yield () As the logger is configured before the application starts, you can use it in any part of your code with the classic scribe usage. import scribe.warn import scribe.cats.io.info def foo: IO[Unit] = info(\"Hello from foo!\") def bar: Unit = warn(\"Hello from bar!\") "
},

{
    "id": 8,
    "uri": "user-guide/20_features/50_observability.html",
    "menu": "user-guide",
    "title": "Observability",
    "text": " Table of Contents Observability Observability This documentation needs to be written. You can help us by contributing to the documentation . "
},

{
    "id": 9,
    "uri": "user-guide/20_features/40_api-server.html",
    "menu": "user-guide",
    "title": "API Server",
    "text": " Table of Contents API Server API Server This documentation needs to be written. You can help us by contributing to the documentation . "
},

{
    "id": 10,
    "uri": "user-guide/20_features/10_configuration.html",
    "menu": "user-guide",
    "title": "Configuration",
    "text": " Table of Contents Configuration Pillars Configuration Application Configuration Configuration Pillars is configured using YAML v1.2 files. Pillars Configuration Pillars configuration is structured as follows: name: Bookstore log: level: info format: enhanced output: type: console db: host: localhost port: 5432 database: bookstore username: postgres password: postgres pool-size: 10 debug: false probe: timeout: PT5s interval: PT10s failure-count: 3 api: enabled: true http: host: 0.0.0.0 port: 9876 enable-logging: true admin: enabled: true http: host: 0.0.0.0 port: 19876 max-connections: 32 observability: enabled: true service-name: bookstore feature-flags: enabled: true flags: - name: feature-1 status: enabled - name: feature-2 status: disabled If you are using the EntryPoint trait, the path to this file must be given to the application using the --config command line option. The config must contain the following keys: name : the name of the application api : the API server configuration admin : the admin server configuration observability : the observability configuration The logging configuration is optional and can be omitted if you are happy with the default configuration. The db and feature-flags sections are needed only if you include the db and feature-flags modules respectively. API Configuration The API configuration is structured as follows: api: enabled: true http: host: 0.0.0.0 port: 9876 enable-logging: true It contains the following keys: enabled : whether the API server is enabled or not http : the HTTP server configuration: host : the host to bind to. Default is 0.0.0.0 , i.e. all interfaces. port : the port to bind to. Default is 9876 . enable-logging : whether to enable HTTP access logging or not. Default is false . Admin Configuration The admin configuration is structured as follows: admin: enabled: true http: host: 0.0.0.0 port: 19876 max-connections: 32 It contains the following keys: enabled : whether the admin server is enabled or not http : the HTTP server configuration: host : the host to bind to. Default is 0.0.0.0 , i.e. all interfaces. port : the port to bind to. Default is 19876 . Observability Configuration The observability configuration is structured as follows: observability: enabled: true service-name: bookstore It contains the following keys: enabled : whether observability is enabled or not service-name : the name of the service.Default is pillars . Logging Configuration The logging configuration is structured as follows: log: level: info format: enhanced output: type: console It contains the following keys: level : the log level. Possible values are trace , debug , info , warn , error . Default is info . format : the log format. Possible values are json , simple , colored , classic , compact , enhanced , advanced , strict . For more details, refer to the scribe documentation . Default is enhanced . output : the log output. type : the log output type. Possible values are console or file . Default is console . path : the path to the log file. It is used only if output.type is file . Database Configuration The database configuration is structured as follows: db: host: localhost port: 5432 database: bookstore username: postgres password: postgres pool-size: 10 debug: false probe: timeout: PT5s interval: PT10s failure-count: 3 It contains the following keys: host : the database host. Default is localhost . port : the database port. Default is 5432 . database : the database name. user : the database user. password : the database password. pool-size : the database connection pool size. Default is 32 . debug : whether to enable database debug logging or not. Default is false . probe : the database probe configuration: timeout : the probe timeout. Default is 5s . interval : the probe interval. Default is 10s . failure-count : the number of consecutive failures before the database is considered down. Default is 3 . Feature Flags Configuration The feature flags configuration is structured as follows: feature-flags: enabled: true flags: - name: feature-1 status: enabled - name: feature-2 status: disabled It contains the following keys: enabled : whether feature flags are enabled or not flags : the feature flags definition: name : the name of the feature flag status : the status of the feature flag. Possible values are enabled or disabled . Application Configuration You can define the configuration of your application in the same file as the Pillars configuration. It must be under the app key. In order to read the configuration, you need to use the configReader method of the Pillars instance. object app extends pillars.EntryPoint: def app: pillars.App[IO] = new pillars.App[IO]: def infos: AppInfo = BuildInfo.toAppInfo def run(using p: Pillars[IO]): IO[Unit] = import p.* for config &lt;- configReader[BookstoreConfig] _ &lt;- IO.println(s\"Config: $config\") yield () The configuration class must be a case class and there must be at least a circe Decoder defined for it. package example import io.circe.Codec case class BookstoreConfig(enabled: Boolean = true, users: UsersConfig = UsersConfig()) object BookstoreConfig: given Codec[BookstoreConfig] = Codec.AsObject.derived case class UsersConfig(init: Boolean = false) object UsersConfig: given Codec[UsersConfig] = Codec.AsObject.derived "
},

{
    "id": 11,
    "uri": "user-guide/20_features/60_admin-server.html",
    "menu": "user-guide",
    "title": "Admin Server",
    "text": " Table of Contents Admin Server Configuration Endpoints Defining administration endpoints Admin Server Pillars provides an administration server that can be used to manage the Pillars server. The administration endpoints are separated from the API server in order to ease security management. As it uses a different port, it can be protected by a firewall or use authentication on an ingress (such as nginx or caddy ). Configuration The configuration is described in the Configuration section. Endpoints By default, the administration server is available on port 19876 and exposes the following endpoints: GET /probes/healthz : the liveness probe. It always returns 200 OK and can be used to check if the server is running. GET /probes/health : the readiness probe. It returns 200 OK if the server is ready to handle requests and all probes are successful. See the Probes section for more details. Modules can add their own endpoints to the administration server. See the Flags section for the feature flags endpoints. Defining administration endpoints You can define administration endpoints easily by defining an adminControllers property in your App . "
},

{
    "id": 12,
    "uri": "user-guide/20_features/30_probes.html",
    "menu": "user-guide",
    "title": "Probes",
    "text": " Table of Contents Probes Liveness Probe Readiness Probe Custom Probes Probes Probes allow you to monitor the health of your application and the underlying infrastructure. Probes are used to determine if a container is ready to accept traffic or if it should be restarted. Liveness Probe A liveness probe checks if the container is still running. If the liveness probe fails, the container is restarted. Pillars defines a default liveness probe. Readiness Probe A readiness probe checks if the container is ready to accept traffic. If the readiness probe fails, the container is not added to the load balancer. The pillars readiness probe aggregates all probes defined in the application. Pillars defines by default a database probe that is enabled if you include the db module . Custom Probes You can define custom probes by implementing the Probe trait. trait Probe[F[_]]: def component: Component // (1) def check: F[Boolean] // (2) def config: ProbeConfig = ProbeConfig() // (3) end Probe 1 The probe component. 2 The check function. If the check function returns true , the probe is considered successful. If is returns false or throws an exception, the probe is considered failed. 3 The probe configuration. "
},

{
    "id": 13,
    "uri": "contribute/20_code_of_conduct.html",
    "menu": "contribute",
    "title": "Code of Conduct",
    "text": " Table of Contents Code of Conduct Our Pledge Our Standards Our Responsibilities Scope Enforcement Enforcement Guidelines Attribution Code of Conduct Our Pledge In the interest of fostering an open and welcoming environment, we as contributors and maintainers pledge to make participation in our project and our community a harassment-free experience for everyone, regardless of age, body size, disability, ethnicity, sex characteristics, gender identity and expression, level of experience, education, socio-economic status, nationality, personal appearance, race, religion, or sexual identity and orientation. Our Standards Examples of behavior that contributes to a positive environment for our community include: Demonstrating empathy and kindness toward other people Being respectful of differing opinions, viewpoints, and experiences Giving and gracefully accepting constructive feedback Accepting responsibility and apologizing to those affected by our mistakes, and learning from the experience Focusing on what is best not just for us as individuals, but for the overall community Examples of unacceptable behavior include: The use of sexualized language or imagery, and sexual attention or advances Trolling, insulting or derogatory comments, and personal or political attacks Public or private harassment Publishing others' private information, such as a physical or email address, without their explicit permission Other conduct which could reasonably be considered inappropriate in a professional setting Our Responsibilities Project maintainers are responsible for clarifying and enforcing our standards of acceptable behavior and will take appropriate and fair corrective action in response to any behavior that they deem inappropriate, threatening, offensive, or harmful. Project maintainers have the right and responsibility to remove, edit, or reject comments, commits, code, wiki edits, issues, and other contributions that are not aligned to this Code of Conduct, and will communicate reasons for moderation decisions when appropriate. Scope This Code of Conduct applies within all community spaces, and also applies when an individual is officially representing the community in public spaces. Examples of representing our community include using an official e-mail address, posting via an official social media account, or acting as an appointed representative at an online or offline event. Enforcement Instances of abusive, harassing, or otherwise unacceptable behavior may be reported to the community leaders responsible for enforcement at contact@rlemaitre.com . All complaints will be reviewed and investigated promptly and fairly. All community leaders are obligated to respect the privacy and security of the reporter of any incident. Enforcement Guidelines Community leaders will follow these Community Impact Guidelines in determining the consequences for any action they deem in violation of this Code of Conduct: 1. Correction Community Impact : Use of inappropriate language or other behavior deemed unprofessional or unwelcome in the community. Consequence : A private, written warning from community leaders, providing clarity around the nature of the violation and an explanation of why the behavior was inappropriate. A public apology may be requested. 2. Warning Community Impact : A violation through a single incident or series of actions. Consequence : A warning with consequences for continued behavior. No interaction with the people involved, including unsolicited interaction with those enforcing the Code of Conduct, for a specified period of time. This includes avoiding interactions in community spaces as well as external channels like social media. Violating these terms may lead to a temporary or permanent ban. 3. Temporary Ban Community Impact : A serious violation of community standards, including sustained inappropriate behavior. Consequence : A temporary ban from any sort of interaction or public communication with the community for a specified period of time. No public or private interaction with the people involved, including unsolicited interaction with those enforcing the Code of Conduct, is allowed during this period. Violating these terms may lead to a permanent ban. 4. Permanent Ban Community Impact : Demonstrating a pattern of violation of community standards, including sustained inappropriate behavior, harassment of an individual, or aggression toward or disparagement of classes of individuals. Consequence : A permanent ban from any sort of public interaction within the community. Attribution This Code of Conduct is adapted from the Contributor Covenant , version 1.4 and 2.0 , and was generated by contributing-gen . "
},

{
    "id": 14,
    "uri": "contribute/10_contributing.html",
    "menu": "contribute",
    "title": "Contributing to Pillars",
    "text": " Table of Contents Contributing to Pillars Code of Conduct I Have a Question I Want To Contribute Style guides Join The Project Team Attribution Contributing to Pillars First off, thanks for taking the time to contribute! ❤️ All types of contributions are encouraged and valued. See the [toc] for different ways to help and details about how this project handles them. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The community looks forward to your contributions. 🎉 And if you like the project, but just don&#8217;t have time to contribute, that&#8217;s fine. There are other easy ways to support the project and show your appreciation, which we would also be very happy about: Star the project Tweet about it Refer this project in your project&#8217;s readme Mention the project at local meetups and tell your friends/colleagues Code of Conduct This project and everyone participating in it is governed by the Pillars Code of Conduct . By participating, you are expected to uphold this code. Please report unacceptable behavior to pillars@rlemaitre.com . I Have a Question If you want to ask a question, we assume that you have read the available Documentation . Before you ask a question, it is best to search for existing Issues that might help you. In case you have found a suitable issue and still need clarification, you can write your question in this issue. It is also advisable to search the internet for answers first. If you then still feel the need to ask a question and need clarification, we recommend the following: Open an Issue . Provide as much context as you can about what you&#8217;re running into. Provide project and platform versions (scala, sbt, etc), depending on what seems relevant. We will then take care of the issue as soon as possible. I Want To Contribute When contributing to this project, you must agree that you have authored 100% of the content, that you have the necessary rights to the content and that the content you contribute may be provided under the project license. Reporting Bugs Before Submitting a Bug Report A good bug report shouldn&#8217;t leave others needing to chase you up for more information. Therefore, we ask you to investigate carefully, collect information and describe the issue in detail in your report. Please complete the following steps in advance to help us fix any potential bug as fast as possible. Make sure that you are using the latest version. Determine if your bug is really a bug and not an error on your side e.g. using incompatible environment components/versions (Make sure that you have read the documentation . If you are looking for support, you might want to check this section . To see if other users have experienced (and potentially already solved) the same issue you are having, check if there is not already a bug report existing for your bug or error in the bug tracker . Also make sure to search the internet (including Stack Overflow) to see if users outside of the GitHub community have discussed the issue. Collect information about the bug: Stack trace (Traceback) OS, Platform and Version (Windows, Linux, macOS, x86, ARM) Version of the interpreter, compiler, SDK, runtime environment, package manager, depending on what seems relevant. Possibly your input and the output Can you reliably reproduce the issue? And can you also reproduce it with older versions? How Do I Submit a Good Bug Report? You must never report security related issues, vulnerabilities or bugs including sensitive information to the issue tracker, or elsewhere in public. Instead, sensitive bugs must be sent by email to security@rlemaitre.com . We use GitHub issues to track bugs and errors. If you run into an issue with the project: Open an Issue . (Since we can&#8217;t be sure at this point whether it is a bug or not, we ask you not to talk about a bug yet and not to label the issue.) Explain the behavior you would expect and the actual behavior. Please provide as much context as possible and describe the reproduction steps that someone else can follow to recreate the issue on their own. This usually includes your code. For good bug reports you should isolate the problem and create a reduced test case. Provide the information you collected in the previous section. Once it&#8217;s filed: The project team will label the issue accordingly. A team member will try to reproduce the issue with your provided steps. If there are no reproduction steps or no obvious way to reproduce the issue, the team will ask you for those steps and mark the issue as needs-repro . Bugs with the needs-repro tag will not be addressed until they are reproduced. If the team is able to reproduce the issue, it will be marked needs-fix , as well as possibly other tags (such as critical ), and the issue will be left to be implemented by someone . Suggesting Enhancements This section guides you through submitting an enhancement suggestion for Pillars, including completely new features and minor improvements to existing functionality . Following these guidelines will help maintainers and the community to understand your suggestion and find related suggestions. Before Submitting an Enhancement Make sure that you are using the latest version. Read the documentation carefully and find out if the functionality is already covered, maybe by an individual configuration. Perform a search to see if the enhancement has already been suggested. If it has, add a comment to the existing issue instead of opening a new one. Find out whether your idea fits with the scope and aims of the project. It&#8217;s up to you to make a strong case to convince the project&#8217;s developers of the merits of this feature. Keep in mind that we want features that will be useful to the majority of our users and not just a small subset. If you&#8217;re just targeting a minority of users, consider writing an add-on/plugin library. How Do I Submit a Good Enhancement Suggestion? Enhancement suggestions are tracked as GitHub issues . Use a clear and descriptive title for the issue to identify the suggestion. Provide a step-by-step description of the suggested enhancement in as many details as possible. Describe the current behavior and explain which behavior you expected to see instead and why. At this point you can also tell which alternatives do not work for you. Explain why this enhancement would be useful to most Pillars users. You may also want to point out the other projects that solved it better and which could serve as inspiration. Your First Code Contribution TBD Improving The Documentation TBD Style guides Commit Messages TBD Join The Project Team TBD Attribution This guide is based on the contributing-gen . Make your own ! "
},

{
    "id": 15,
    "uri": "search.html",
    "menu": "-",
    "title": "search",
    "text": " Search Results "
},

{
    "id": 16,
    "uri": "lunrjsindex.html",
    "menu": "-",
    "title": "null",
    "text": " will be replaced by the index "
},

];
