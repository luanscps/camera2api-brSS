# MediaMTX — Setup rapido para receber RTMP do celular

## 1. Baixar MediaMTX

Download em https://github.com/bluenviron/mediamtx/releases (escolha a versao para Windows/Linux/Mac)

## 2. Rodar (sem configurar nada)

```bash
./mediamtx
```

O MediaMTX aceita RTMP por padrao na porta **1935**.

## 3. Configurar o IP no app

No app, va em **Destino RTMP** e coloque:
```
rtmp://SEU_IP_LOCAL:1935/live/stream
```
Exemplo: `rtmp://192.168.1.100:1935/live/stream`

Voce pode tambem editar direto em `StreamingService.kt`:
```kotlin
var rtmpUrl = "rtmp://192.168.1.100:1935/live/stream"
```

## 4. Abrir no OBS

No OBS, adicione uma **Fonte de Midia** ou va em **Configuracoes > Stream**:
- Servico: Personalizado
- Servidor: `rtmp://localhost:1935/live`
- Chave: `stream`

Ou assista via VLC:
```
rtsp://localhost:8554/live/stream   <- MediaMTX reemite como RTSP tambem
http://localhost:8888/live/stream   <- HLS
```

## 5. Por que MediaMTX?

- Mantem o stream ativo mesmo sem clientes conectados
- Reemite automaticamente como RTSP, HLS, WebRTC, SRT
- Zero configuracao para uso basico
- Compativel com OBS, VLC, FFmpeg, navegador
