def gitUrl = 'https://github.com/shikunwei/springboot-helloworld.git'

createCiJob("spring-boot-hello-world", gitUrl, "pom.xml")
createSonarJob("spring-boot-hello-world", gitUrl, "pom.xml")
createDockerBuildJob("spring-boot-hello-world")
createDockerStartJob("spring-boot-hello-world", "48080")
createDockerStopJob("spring-boot-hello-world")


def createCiJob(def jobName, def gitUrl, def pomFile) {
  job("${jobName}-1-ci") {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
      stringParam("REPOSITORY_URL", "http://nexus:8081/repository/maven-releases/", "Nexus Release Repository URL")
    }
    scm {
      git {
        remote {
          url(gitUrl)
        }
        extensions {
          cleanAfterCheckout()
        }
      }
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    triggers {
      scm('30/H * * * *')
      githubPush()
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=DEV-\${BUILD_NUMBER}')
          mavenInstallation('M3')
          rootPOM( pomFile )
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings('bc30ebe0-68e1-4fa7-ab30-38092113a63c')
      }
      maven {
        goals('clean deploy')
        mavenInstallation('M3')
        rootPOM(pomFile)
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('bc30ebe0-68e1-4fa7-ab30-38092113a63c')
      }
    }
    publishers {
      chucknorris()
      archiveXUnit {
        jUnit {
          pattern('**/target/surefire-reports/*.xml')
          skipNoTestFiles(true)
          stopProcessingIfError(true)
        }
      }
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      downstreamParameterized {
        trigger("${jobName}-2-sonar") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createSonarJob(def jobName, def gitUrl, def pomFile) {
  job("${jobName}-2-sonar") {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
    }
    scm {
      cloneWorkspace("${jobName}-1-ci")
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    steps {
      maven {
        goals('org.jacoco:jacoco-maven-plugin:prepare-agent install -Psonar')
        mavenInstallation('M3')
        rootPOM(pomFile)
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('bc30ebe0-68e1-4fa7-ab30-38092113a63c')
      }
      maven {
        goals('sonar:sonar -Psonar')
        mavenInstallation('M3')
        rootPOM(pomFile)
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('bc30ebe0-68e1-4fa7-ab30-38092113a63c')
      }
    }
    publishers {
      chucknorris()
      downstreamParameterized {
        trigger("${jobName}-3-docker-build") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createDockerBuildJob(def jobName) {

  println "############################################################################################################"
  println "Creating Docker Build Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-3-docker-build") {
    logRotator {
        numToKeep(10)
    }
    scm {
      cloneWorkspace("${jobName}-1-ci")
    }
    steps {
      steps {
        shell("sudo /usr/bin/docker build -t ${jobName} .")
      }
    }
    publishers {
      chucknorris()
      downstreamParameterized {
        trigger("${jobName}-4-docker-start-container") {
          parameters {
            currentBuild()
          }
        }
      }
    }
  }
}

def createDockerStartJob(def jobName, def port) {

  println "############################################################################################################"
  println "Creating Docker Start Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-4-docker-start-container") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell('echo "Stopping Docker Container first"')
        shell("sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter=\"name=${jobName}\") | true ")
        shell("sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter=\"name=${jobName}\") | true ")
        shell('echo "Starting Docker Container"')
        shell("sudo /usr/bin/docker run -d --name ${jobName} -p=${port}:8080 ${jobName}")
      }
    }
    publishers {
      chucknorris()
    }
  }
}

def createDockerStopJob(def jobName) {

  println "############################################################################################################"
  println "Creating Docker Stop Job for ${jobName} "
  println "############################################################################################################"

  job("${jobName}-5-docker-stop-container") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell("sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter=\"name=${jobName}\")")
        shell("sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter=\"name=${jobName}\")")
      }
    }
    publishers {
      chucknorris()
    }
  }
}

buildPipelineView('Pipeline') {
    filterBuildQueue()
    filterExecutors()
    title('HelloWorld CI Pipeline')
    displayedBuilds(5)
    selectedJob("spring-boot-hello-world-1-ci")
    alwaysAllowManualTrigger()
    refreshFrequency(60)
}

listView('HelloWorld') {
    description('')
    filterBuildQueue()
    filterExecutors()
    jobs {
        regex(/spring-boot-hello-world-.*/)
    }
    columns {
        status()
        buildButton()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
    }
}
