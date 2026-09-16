import { useEffect, useRef, useState, type RefObject } from "react";

type CameraCapture = {
  cameraOpen: boolean;
  cameraVideoRef: RefObject<HTMLVideoElement | null>;
  cameraCanvasRef: RefObject<HTMLCanvasElement | null>;
  cameraError: string;
  openCamera: () => Promise<void>;
  capturePhoto: () => void;
  stopCamera: () => void;
};

export function useCameraCapture(onSourceFileChange: (file: File) => void): CameraCapture {
  const cameraVideoRef = useRef<HTMLVideoElement>(null);
  const cameraCanvasRef = useRef<HTMLCanvasElement>(null);
  const cameraStreamRef = useRef<MediaStream | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraError, setCameraError] = useState("");

  const stopCamera = () => {
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
    cameraStreamRef.current = null;
    setCameraOpen(false);
  };

  useEffect(() => () => {
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
  }, []);

  useEffect(() => {
    if (!cameraOpen || !cameraVideoRef.current || !cameraStreamRef.current) return;
    const video = cameraVideoRef.current;
    video.srcObject = cameraStreamRef.current;
    void video.play().catch(() => undefined);

    return () => {
      video.srcObject = null;
    };
  }, [cameraOpen]);

  const openCamera = async () => {
    setCameraError("");
    if (!globalThis.navigator.mediaDevices?.getUserMedia) {
      setCameraError("Trình duyệt không hỗ trợ camera. Bạn có thể dùng Tải tệp.");
      return;
    }

    try {
      stopCamera();
      const stream = await globalThis.navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" } },
        audio: false,
      });
      cameraStreamRef.current = stream;
      setCameraOpen(true);
    } catch {
      setCameraError("Không thể mở camera. Hãy cấp quyền camera hoặc dùng Tải tệp.");
    }
  };

  const capturePhoto = () => {
    const video = cameraVideoRef.current;
    const canvas = cameraCanvasRef.current;
    if (!video || !canvas || !video.videoWidth || !video.videoHeight) {
      setCameraError("Camera chưa sẵn sàng, hãy thử lại.");
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    if (!context) {
      setCameraError("Không thể chụp ảnh. Bạn có thể dùng Tải tệp.");
      return;
    }

    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(blob => {
      if (!blob) {
        setCameraError("Không thể chụp ảnh. Bạn có thể dùng Tải tệp.");
        return;
      }

      onSourceFileChange(new File([blob], `physlive-photo-${Date.now()}.jpg`, { type: "image/jpeg" }));
      stopCamera();
    }, "image/jpeg", 0.92);
  };

  return { cameraOpen, cameraVideoRef, cameraCanvasRef, cameraError, openCamera, capturePhoto, stopCamera };
}

