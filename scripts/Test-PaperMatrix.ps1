param(
   [string[]]$MinecraftVersions = @('1.21.11', '26.1.1', '26.1.2', '26.2', '26.3'),
   [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pluginJar = Join-Path $projectRoot 'target\StringDuperFix-1.0.0.jar'
$testRoot = Join-Path $projectRoot 'target\paper-matrix'
$java = (Get-Command java -ErrorAction Stop).Source

if (-not (Test-Path -LiteralPath $pluginJar)) {
   throw "Plugin JAR is missing: $pluginJar"
}

$results = foreach ($version in $MinecraftVersions) {
   Write-Host "=== Paper $version ==="
   $metadataUrl = "https://fill.papermc.io/v3/projects/paper/versions/$version/builds/latest"
   $build = Invoke-RestMethod -Uri $metadataUrl
   $download = $build.downloads.'server:default'
   if ($null -eq $download) {
      throw "Paper did not return a default server download for $version"
   }

   $serverDir = Join-Path $testRoot $version
   $pluginsDir = Join-Path $serverDir 'plugins'
   New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null
   $pluginDataDir = Join-Path $pluginsDir 'StringDuperFix'
   $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
   $resolvedPluginData = [IO.Path]::GetFullPath($pluginDataDir)
   if (-not $resolvedPluginData.StartsWith($resolvedTestRoot, [StringComparison]::OrdinalIgnoreCase)) {
      throw "Refusing to clean a plugin data path outside the test root: $resolvedPluginData"
   }
   if (Test-Path -LiteralPath $pluginDataDir) {
      Remove-Item -LiteralPath $pluginDataDir -Recurse -Force
   }
   $serverJar = Join-Path $serverDir $download.name
   if (-not (Test-Path -LiteralPath $serverJar)) {
      Invoke-WebRequest -UseBasicParsing -Uri $download.url -OutFile $serverJar
   }
   $actualServerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverJar).Hash.ToLowerInvariant()
   if ($actualServerHash -ne $download.checksums.sha256.ToLowerInvariant()) {
      throw "Checksum mismatch for Paper $version"
   }

   Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $pluginsDir 'StringDuperFix-1.0.0.jar') -Force
   Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Encoding ASCII -Value 'eula=true'
   Set-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Encoding ASCII -Value @(
      'online-mode=false'
      'level-type=minecraft:flat'
      'spawn-protection=0'
      'view-distance=2'
      'simulation-distance=2'
      'motd=String Duper Fix compatibility test'
   )

   $psi = [Diagnostics.ProcessStartInfo]::new()
   $psi.FileName = $java
   $psi.WorkingDirectory = $serverDir
   $psi.UseShellExecute = $false
   $psi.CreateNoWindow = $true
   $psi.RedirectStandardInput = $true
   $psi.RedirectStandardOutput = $true
   $psi.RedirectStandardError = $true
   $null = $psi.ArgumentList.Add('-Xms512M')
   $null = $psi.ArgumentList.Add('-Xmx1G')
   $null = $psi.ArgumentList.Add('-jar')
   $null = $psi.ArgumentList.Add($serverJar)
   $null = $psi.ArgumentList.Add('--nogui')

   $process = [Diagnostics.Process]::new()
   $process.StartInfo = $psi
   $null = $process.Start()
   $stdout = $process.StandardOutput.ReadToEndAsync()
   $stderr = $process.StandardError.ReadToEndAsync()
   $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
   $started = $false
   $pluginEnabled = $false
   $failure = $null
   $latestLog = Join-Path $serverDir 'logs\latest.log'

   while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 500
      if (-not (Test-Path -LiteralPath $latestLog)) {
         continue
      }
      $log = Get-Content -LiteralPath $latestLog -Raw -ErrorAction SilentlyContinue
      $pluginEnabled = $log -match 'String Duper Fix 1\.0\.0 is active\.'
      $started = $log -match 'Done \([0-9.,]+s\)! For help, type "help"'
      if ($log -match '(?im)^.*(?:ERROR|Exception).*StringDuperFix') {
         $failure = 'Plugin-related exception in latest.log'
         break
      }
      if ($log -match 'Invalid sound in sounds\.yml') {
         $failure = 'A configured sound was rejected'
         break
      }
      if ($log -match 'Mechanics configuration was not found: .*leaf-global\.yml') {
         $failure = 'A normal Paper server was treated as a missing Leaf installation'
         break
      }
      if ($started -and $pluginEnabled) {
         break
      }
   }

   if (-not $process.HasExited) {
      $process.StandardInput.WriteLine('stop')
      $process.StandardInput.Flush()
      if (-not $process.WaitForExit(60000)) {
         $process.Kill($true)
         $failure = 'Server did not stop within 60 seconds'
      }
   }

   $console = $stdout.GetAwaiter().GetResult() + [Environment]::NewLine + $stderr.GetAwaiter().GetResult()
   Set-Content -LiteralPath (Join-Path $serverDir 'console.log') -Encoding UTF8 -Value $console
   if (-not $started -and $null -eq $failure) {
      $failure = 'Server startup did not finish before the timeout'
   }
   if (-not $pluginEnabled -and $null -eq $failure) {
      $failure = 'String Duper Fix did not report a successful enable'
   }

   [pscustomobject]@{
      Minecraft = $version
      PaperBuild = $build.id
      Channel = $build.channel
      ServerStarted = $started
      PluginEnabled = $pluginEnabled
      Result = if ($null -eq $failure) { 'PASS' } else { "FAIL: $failure" }
   }
}

$results | Format-Table -AutoSize
if ($results.Result -match '^FAIL') {
   exit 1
}
