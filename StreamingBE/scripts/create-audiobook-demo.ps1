$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$mediaDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\media\audiobooks'))
$catalogPath = Join-Path $mediaDirectory 'catalog.json'
if (Test-Path -LiteralPath $catalogPath) {
    throw 'A catalogue already exists. Keep it and add books manually; this script never overwrites it.'
}
New-Item -ItemType Directory -Force -Path (Join-Path $mediaDirectory 'demo') | Out-Null
$chapters = @(
    @{ title = 'A light across the water'; text = 'The lantern keeper had a simple rule. Every evening, before the last fishing boat returned, she climbed the narrow stairs and lit the lamp. One autumn night, the sea was perfectly still. Across the water, a small light appeared on an island that had been empty for years. It flashed once, then twice. Mara set down her tea and watched. The light flashed again. She found her old boat behind the workshop, checked the rope, and pushed it into the shallows. In her pocket she carried a compass, a piece of bread, and the brass key her father had given her. By the time she reached the island, the moon had risen. Someone had left a lantern on the sand. Beside it was a note: Thank you for keeping the light on.' },
    @{ title = 'The way home'; text = 'Mara followed a trail of footprints to the old shelter. Inside, a young traveller sat beside a sleeping dog. Their boat had lost its sail in the afternoon wind, and they had reached the island just before dark. We saw your light, the traveller said. We knew there was someone on the other shore. Mara smiled and divided the bread into three pieces. Together they carried the lantern back to the little boat. The dog settled beneath the seat, and the traveller took an oar. As they crossed the quiet water, the lighthouse swept a golden path across the bay. Mara had always thought her work was about warning people away from the rocks. That night she understood something else. Sometimes a light was simply a promise that someone would be waiting when you found your way home. The end.' }
)
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $catalogChapters = @()
    for ($index = 0; $index -lt $chapters.Count; $index++) {
        $relative = 'demo/chapter-' + ($index + 1) + '.wav'
        $audioPath = Join-Path $mediaDirectory $relative
        $synth.SetOutputToWaveFile($audioPath, $format)
        $synth.Speak($chapters[$index].text)
        $synth.SetOutputToNull()
        $reader = [IO.BinaryReader]::new([IO.File]::OpenRead($audioPath))
        try {
            $reader.BaseStream.Position = 12
            $bytesPerSecond = 32000
            $dataLength = 0
            while ($reader.BaseStream.Position -lt $reader.BaseStream.Length - 8) {
                $chunk = [Text.Encoding]::ASCII.GetString($reader.ReadBytes(4))
                $size = $reader.ReadUInt32()
                $next = $reader.BaseStream.Position + $size + ($size % 2)
                if ($chunk -eq 'fmt ') {
                    $reader.BaseStream.Position += 8
                    $bytesPerSecond = $reader.ReadUInt32()
                }
                if ($chunk -eq 'data') { $dataLength = $size; break }
                $reader.BaseStream.Position = $next
            }
            $duration = [Math]::Round($dataLength / $bytesPerSecond, 3)
        } finally { $reader.Dispose() }
        $catalogChapters += @{ title = $chapters[$index].title; source = $relative; durationSeconds = $duration }
    }
    $book = @{
        id = 'the-lantern-keeper'; title = 'The Lantern Keeper'; author = 'Streaming App demo';
        narrator = 'Windows synthetic voice'; description = 'An original two-chapter sample story for testing playback, chapter changes, and resume.';
        coverUrl = $null; chapters = $catalogChapters
    }
    $json = ConvertTo-Json -InputObject @($book) -Depth 5
    [IO.File]::WriteAllText($catalogPath, $json, [Text.UTF8Encoding]::new($false))
    Write-Output "Created original sample audiobook at $catalogPath"
} finally { $synth.Dispose() }
