const encoder = new TextEncoder();
const decoder = new TextDecoder();

/** ttyd message types are ASCII characters: '0' = 48, '1' = 49, '2' = 50 */
const INPUT_TYPE = "0".charCodeAt(0); // 48 - stdin input
const RESIZE_TYPE = "1".charCodeAt(0); // 49 - resize
const OUTPUT_TYPE = "0".charCodeAt(0); // 48 - stdout output (server->client)

/** Encode user input for ttyd binary protocol (type '0' + UTF-8 payload) */
export function encodeTtydInput(data: string): ArrayBuffer {
  const payload = encoder.encode(data);
  const msg = new Uint8Array(1 + payload.length);
  msg[0] = INPUT_TYPE;
  msg.set(payload, 1);
  return msg.buffer;
}

/** Encode resize event for ttyd binary protocol (type '1' + JSON payload) */
export function encodeTtydResize(cols: number, rows: number): ArrayBuffer {
  const json = JSON.stringify({ columns: cols, rows: rows });
  const payload = encoder.encode(json);
  const msg = new Uint8Array(1 + payload.length);
  msg[0] = RESIZE_TYPE;
  msg.set(payload, 1);
  return msg.buffer;
}

export interface TtydMessage {
  type: number;
  payload: string;
}

/** Decode a ttyd binary message from the server */
export function decodeTtydMessage(data: ArrayBuffer): TtydMessage {
  const view = new Uint8Array(data);
  return {
    type: view[0],
    payload: decoder.decode(view.subarray(1)),
  };
}

/** Check if a ttyd message is stdout output (type '0') */
export function isOutputMessage(msg: TtydMessage): boolean {
  return msg.type === OUTPUT_TYPE;
}
