# Mini client RCON (protocole Source) - usage interne de test
param([Parameter(Mandatory=$true)][string]$Cmd,
      [string]$Pass='hcrcon',[string]$RHost='127.0.0.1',[int]$Port=25575)

function New-RconPacket([int]$id,[int]$type,[string]$body){
    $b=[System.Text.Encoding]::ASCII.GetBytes($body)
    $len=10+$b.Length
    $ms=New-Object System.IO.MemoryStream
    $ms.Write([BitConverter]::GetBytes([int]$len),0,4)
    $ms.Write([BitConverter]::GetBytes([int]$id),0,4)
    $ms.Write([BitConverter]::GetBytes([int]$type),0,4)
    if($b.Length){ $ms.Write($b,0,$b.Length) }
    $ms.Write([byte[]]@(0,0),0,2)
    return $ms.ToArray()
}
function Read-RconPacket($stream){
    $hdr=New-Object byte[] 4
    $n=$stream.Read($hdr,0,4); if($n -lt 4){ return $null }
    $len=[BitConverter]::ToInt32($hdr,0)
    $buf=New-Object byte[] $len; $off=0
    while($off -lt $len){ $r=$stream.Read($buf,$off,$len-$off); if($r -le 0){break}; $off+=$r }
    $id=[BitConverter]::ToInt32($buf,0)
    $type=[BitConverter]::ToInt32($buf,4)
    $txt=[System.Text.Encoding]::ASCII.GetString($buf,8,$len-10)
    return [pscustomobject]@{Id=$id;Type=$type;Text=$txt}
}

$tcp=New-Object System.Net.Sockets.TcpClient
$tcp.Connect($RHost,$Port)
$tcp.NoDelay=$true
$s=$tcp.GetStream()

$p=New-RconPacket 1 3 $Pass
$s.Write($p,0,$p.Length); $s.Flush()
$auth=Read-RconPacket $s
if($null -eq $auth -or $auth.Id -eq -1){ Write-Output "AUTH_FAILED"; $tcp.Close(); exit 1 }

$p=New-RconPacket 2 2 $Cmd
$s.Write($p,0,$p.Length); $s.Flush()
Start-Sleep -Milliseconds 400
$resp=Read-RconPacket $s
if($resp){ Write-Output $resp.Text } else { Write-Output "(aucune reponse)" }
$tcp.Close()
