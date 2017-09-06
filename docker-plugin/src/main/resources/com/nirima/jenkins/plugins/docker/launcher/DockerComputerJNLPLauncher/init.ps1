$CONFIG="c:\config.ps1"

while ( -not (Test-Path $CONFIG)){
    Write-Output "No config, sleeping for 1 second"
    sleep 1
}


Write-Output "Found config file"
. "$CONFIG"
# require:
# $JENKINS_URL
# $COMPUTER_URL
# $COMPUTER_SECRET

if (!$JENKINS_URL){
 echo "JENKINS_URL is not defined! Exiting."
 exit 1
}

if (!$COMPUTER_URL){
 echo "COMPUTER_URL is not defined! Exiting."
 exit 1
}


# Jenkins not at home - use tmp
if (-not (Test-Path $JENKINS_HOME)){
  New-Item -ItemType directory -Path c:\Temp
  $JENKINS_HOME="c:\Temp"
}

Write-Output "###################################"
Write-Output "JENKINS_URL = $JENKINS_URL         "
Write-Output "JENKINS_USER = $JENKINS_USER       "
Write-Output "JENKINS_HOME = $JENKINS_HOME       "
Write-Output "COMPUTER_URL = $COMPUTER_URL       "
Write-Output "COMPUTER_SECRET = $COMPUTER_SECRET "
Write-Output "env:CMD_OPTS = $env:CMD_OPTS       "
Write-Output "###################################"

Get-Item Env:

cd "$JENKINS_HOME"


(New-Object System.Net.WebClient).DownloadFile("${JENKINS_URL}/jnlpJars/slave.jar", "${JENKINS_HOME}/slave.jar")

$slaveName=$COMPUTER_URL -replace "computer/", "" -replace "/", ""
$RUN_OPTS="hudson.remoting.jnlp.Main -headless -url $JENKINS_URL $COMPUTER_SECRET $slaveName "

$RUN_CMD="java $env:CMD_OPTS -cp ${JENKINS_HOME}\\slave.jar $RUN_OPTS"
Write-Output "RUN_OPTS = $RUN_CMD"
Invoke-Expression $RUN_CMD
