import { useEffect, useRef } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { useDocumentStore } from '@/stores/useDocumentStore'

/**
 * Subscribes to document status changes via STOMP WebSocket.
 * Pass documentIds to subscribe to specific documents, or omit to skip.
 */
export function useDocumentNotification(documentIds: string[]) {
  const clientRef = useRef<Client | null>(null)
  const updateStatus = useDocumentStore((s) => s.updateDocumentStatus)

  useEffect(() => {
    if (documentIds.length === 0) return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws/notifications'),
      reconnectDelay: 5000,
      onConnect: () => {
        documentIds.forEach((docId) => {
          client.subscribe(`/topic/documents/${docId}`, (msg) => {
            const event = JSON.parse(msg.body)
            if (event.type === 'DOCUMENT_STATUS_CHANGED') {
              const { documentId, status } = event.payload
              updateStatus(documentId, status)
            }
          })
        })
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [documentIds.join(','), updateStatus])
}
