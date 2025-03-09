<div class="hero">
    <h1 class="hero__title">Pillars</h1>
    <p class="hero__description">Build your backend service in Scala 3 the easy way</p>
</div>
<div class="hero__terminal">
    <pre>
        <code class="language-scala">
import ... // import your dependencies

object app extends pillars.IOApp(DB, DBMigration, FeatureFlags, HttpClient): //define your apps modules
  def infos: AppInfo = BuildInfo.toAppInfo // automatic description from your build

  def run: Run[IO, IO[Unit]] = // enjoy!
    for
      _ <- logger.info(s"📚 Welcome to \${Config[IO].name}!") // logging is included in pillars-core
      _ <- dbMigration.migrate("classpath:db/migrations")     // you can access directly your modules
      _ <- flag"feature-1".whenEnabled:                       // handy feature flag interpolation
            db.use: session =>
              for
                date <- session.unique(sql"select now()".query(timestamptz))
                _    <- Logger[IO].info(s"The current date is \$date.")
              yield ()
      _ <- http.get("https://pillars.dev"): response =>
            logger.info(s"Response: \${response.status}")
      _ <- server.start(homeController, userController)        // start your server with your controllers
    yield ()
    end for
  end run
end app
        </code>
    </pre>
</div>
<div class="wrapper">
    <div class="installation">
        <h3 class="section__title">Installation</h3>
        <div class="tab__container">
            <ul class="tab__menu">
                <li class="tab active" data-tab="sbt">sbt</li>
                <li class="tab" data-tab="mill">mill</li>
                <li class="tab" data-tab="scala-cli">scala-cli</li>
                <li class="tab" data-tab="pants">pants</li>
                <li class="tab" data-tab="gradle">Gradle</li>
                <li class="tab" data-tab="maven">Maven</li>
            </ul>
            <pre class="code tab__pane active sbt">
                <code class="highlight language-scala">libraryDependencies ++= Seq("io.funktional" %% "pillars-core" % "${config.site_version}")</code>
            </pre>
            <pre class="code tab__pane mill">
                <code class="highlight language-scala">ivy"io.funktional::pillars-core:${config.site_version}"</code>
            </pre>
            <pre class="code tab__pane scala-cli">
                <code class="highlight language-scala">//> using dep io.funktional::pillars-core:${config.site_version}</code>
            </pre>
            <pre class="code tab__pane pants">
                <code class="highlight language-scala">
scala_artifact(
    group="io.funktional",
    artifact="pillars-core",
    version="${config.site_version}",
    packages=["pillars.**"],
)
                </code>
            </pre>
            <pre class="code tab__pane gradle">
                <code class="highlight language-gradle">implementation 'io.funktional:pillars-core:${config.site_version}'</code>
            </pre>
            <pre class="code tab__pane maven">
                <code class="highlight language-xml">
&lt;dependency>
    &lt;groupId>io.funktional&lt;/groupId>
    &lt;artifactId>pillars-core&lt;/artifactId>
    &lt;version>${config.site_version}&lt;/version>
&lt;/dependency>
                </code>
            </pre>
        </div>
    </div>
    <div class="feature">
        <div class="feature__item">
            <h3 class="section__title">Modularity</h3>
            <p>It offers a modular structure, allowing developers to use only the components they need, resulting in efficient and streamlined applications.</p>
        </div>
        <div class="feature__item">
            <h3 class="section__title">Observability</h3>
            <p>Using OpenTelemetry, Pillars includes built-in support for observability, allowing you to monitor and understand the behavior of your application. This can be crucial for identifying and resolving issues quickly.</p>
        </div>
        <div class="feature__item">
            <h3 class="section__title">Performance</h3>
            <p>Pillars uses <a href="https://netty.io">Netty</a> for HTTP servers and clients, which is known for its high performance and scalability. This can help to ensure that your application remains responsive under heavy load.</p>
        </div>
        <div class="feature__item">
            <h3 class="section__title">Admin Server</h3>
            <p>Pillars includes an admin server feature, providing a separate interface for administrative tasks. This can help to keep your main application server focused on serving user requests.</p>
        </div>
        <div class="feature__item">
            <h3 class="section__title">Database Access</h3>
            <p>Pillars provides a streamlined interface for interacting with databases. This can help to reduce the complexity of your codebase and make it easier to manage data operations.</p>
        </div>
        <div class="feature__item">
            <h3 class="section__title">Feature Flags</h3>
            <p>Pillars supports feature flags, allowing you to toggle features on and off without needing to redeploy your application. This can be particularly useful for testing new features or managing rollouts.</p>
        </div>
    </div>
    <div class="callout">
        <p>Read our documentation for how to use Pillars</p>
        <a href="user-guide/" class="button--primary">Documentation</a>
    </div>
</div>
