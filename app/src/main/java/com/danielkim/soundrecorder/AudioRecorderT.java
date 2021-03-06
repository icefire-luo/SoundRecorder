package com.danielkim.soundrecorder;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;

 public class AudioRecorderT {
        // 缓冲区字节大小
        private int bufferSizeInBytes = 0;

        //AudioFileName裸音频数据文件 ，麦克风
        private String AudioFileName = "";
        private String AudioWavName = "";

        //NewAudioFileName可播放的音频文件

        private AudioRecord audioRecord;
        private boolean isRecord = false;// 设置正在录制的状态

        private int channelconfig = AudioFormat.CHANNEL_IN_MONO;
        private int channelcount = getChannelCount();
        private int audioSource = MediaRecorder.AudioSource.MIC;
        private int frequence = 8000;
        private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

        private String filePath;

        private long startHTime = 0L;
        private long timeInMilliseconds = 0L;

        private static AudioRecorderT mInstance;

        private int test1, test2;



        private AudioRecorderT() {

        }

        public synchronized static AudioRecorderT getInstance() {
            if (mInstance == null) {
                mInstance = new AudioRecorderT();
            }
            return mInstance;
        }

        private int getChannelCount() {
            return (channelconfig ==AudioFormat.CHANNEL_CONFIGURATION_MONO)?1:2;
        }
        public int startRecordAndFile() {
            //判断是否有外部存储设备sdcard
            if (AudioFileT.isSdcardExit()) {
                if (isRecord) {
                    return AudioErrorT.E_STATE_RECODING;
                } else {

                    creatAudioRecord();

                    audioRecord.startRecording();
                    // 让录制状态为true
                    isRecord = true;
                    // 开启音频文件写入线程
                    new Thread(new AudioRecordThread()).start();
                    // 记录起始时间
                    startHTime = SystemClock.uptimeMillis();
                    timeInMilliseconds = 0;

                    return AudioErrorT.SUCCESS;
                }

            } else {
                return AudioErrorT.E_NOSDCARD;
            }

        }

        public void stopRecordAndFile() {

            close();
            startHTime = 0L;
            timeInMilliseconds = 0L;
        }


        public long getRecordFileSize() {
            return AudioFileT.getFileSize(filePath);
        }


        private void close() {
            if (audioRecord != null) {
                System.out.println("stopRecord");
                isRecord = false;//停止文件写入
                audioRecord.stop();
                audioRecord.release();//释放资源
                audioRecord = null;
            }

        }


        private void creatAudioRecord() {

            // 获取音频文件路径
            SimpleDateFormat dateformat = new SimpleDateFormat("yyMMdd_HHmmss");
            String dateStr = dateformat.format(System.currentTimeMillis());
            //AudioFileName = filePath + "/" + audioSource + "_" + frequence + "_" + channelconfig + "_" + audioEncoding + "_" + (System.currentTimeMillis() % 1_000_000) + ".pcm";
            //AudioWavName = filePath + "/" + audioSource + "_" + frequence + "_" + channelconfig + "_" + audioEncoding + "_" + (System.currentTimeMillis() % 1_000_000) + ".wav";
            //AudioFileName = filePath + "/" + (System.currentTimeMillis() / 1000) + "_" + audioSource + "_" + frequence + "_" + channelconfig + "_" + audioEncoding + ".pcm";
            //AudioWavName = filePath + "/" + (System.currentTimeMillis() / 1000) + "_" + audioSource + "_" + frequence + "_" + channelconfig + "_" + audioEncoding + ".wav";
            AudioFileName = filePath + "/" + (dateStr) + "_" + (frequence/1000) + "k_" + channelconfig + ".pcm";
            AudioWavName = filePath + "/" + (dateStr) + "_" + (frequence/1000) + "k_" + channelconfig + ".wav";

            // 获得缓冲区字节大小
            bufferSizeInBytes = AudioRecord.getMinBufferSize(
                    frequence, channelconfig, audioEncoding);

            // 创建AudioRecord对象
            try {
                audioRecord = new AudioRecord(
                        audioSource, frequence, channelconfig,
                        audioEncoding, bufferSizeInBytes);
            } catch (Exception e){
                if(e.getMessage() != null) {
                    Log.e("LUO_DBG_HAHAHA", e.getMessage());
                } else {
                    Log.e("LUO_DBG_HAHAHA", "WULALALAL");
                }
            }

        }


        class AudioRecordThread implements Runnable {
            @Override
            public void run() {

                writeDateTOFile();//往文件中写入裸数据
                //copyWaveFile(AudioFileName, AudioWavName);//给裸数据加上头文件
            }
        }

        /**
         * 这里将数据写入文件，但是并不能播放，因为AudioRecord获得的音频是原始的裸音频，
         * 如果需要播放就必须加入一些格式或者编码的头信息。但是这样的好处就是你可以对音频的 裸数据进行处理，比如你要做一个爱说话的TOM
         * 猫在这里就进行音频的处理，然后重新封装 所以说这样得到的音频比较容易做一些音频的处理。
         */
        private void writeDateTOFile() {
            // new一个byte数组用来存一些字节数据，大小为缓冲区大小
            byte[] audiodata = new byte[bufferSizeInBytes];
            FileOutputStream fos = null;
            int readsize = 0;
            try {
                File file = new File(AudioFileName);
                if (file.exists()) {
                    file.delete();
                }
                fos = new FileOutputStream(file);// 建立一个可存取字节的文件
            } catch (Exception e) {
                e.printStackTrace();
            }
            while (isRecord == true) {

                readsize = audioRecord.read(audiodata, 0, bufferSizeInBytes);

                if (AudioRecord.ERROR_INVALID_OPERATION != readsize && fos != null) {
                    try {
                        fos.write(audiodata);
                        // 计算以录音时间长度
                        timeInMilliseconds = SystemClock.uptimeMillis() - startHTime;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                if (fos != null)
                    fos.close();// 关闭写入流
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 这里得到可播放的音频文件
        private void copyWaveFile(String inFilename, String outFilename) {
            FileInputStream in = null;
            FileOutputStream out = null;
            long totalAudioLen = 0;
            long totalDataLen = totalAudioLen + 36;
            long byteRate = 16 * frequence * getChannelCount() / 8;
            byte[] data = new byte[bufferSizeInBytes];
            try {
                in = new FileInputStream(inFilename);
                out = new FileOutputStream(outFilename);
                totalAudioLen = in.getChannel().size();
                totalDataLen = totalAudioLen + 36;
                writeWaveFileHeader(out, totalAudioLen, totalDataLen,
                        frequence, channelconfig, byteRate);
                while (in.read(data) != -1) {
                    out.write(data);
                }
                in.close();
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                         long totalDataLen, long longSampleRate, int channelconfigs, long byteRate)
                throws IOException {
            byte[] header = new byte[44];
            header[0] = 'R'; // RIFF/WAVE header
            header[1] = 'I';
            header[2] = 'F';
            header[3] = 'F';
            header[4] = (byte) (totalDataLen & 0xff);
            header[5] = (byte) ((totalDataLen >> 8) & 0xff);
            header[6] = (byte) ((totalDataLen >> 16) & 0xff);
            header[7] = (byte) ((totalDataLen >> 24) & 0xff);
            header[8] = 'W';
            header[9] = 'A';
            header[10] = 'V';
            header[11] = 'E';
            header[12] = 'f'; // 'fmt ' chunk
            header[13] = 'm';
            header[14] = 't';
            header[15] = ' ';
            header[16] = 16; // 4 bytes: size of 'fmt ' chunk
            header[17] = 0;
            header[18] = 0;
            header[19] = 0;
            header[20] = 1; // format = 1
            header[21] = 0;
            header[22] = (byte) getChannelCount();
            header[23] = 0;
            header[24] = (byte) (longSampleRate & 0xff);
            header[25] = (byte) ((longSampleRate >> 8) & 0xff);
            header[26] = (byte) ((longSampleRate >> 16) & 0xff);
            header[27] = (byte) ((longSampleRate >> 24) & 0xff);
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = (byte) (2 * 16 / 8); // block align
            header[33] = 0;
            header[34] = 16; // bits per sample
            header[35] = 0;
            header[36] = 'd';
            header[37] = 'a';
            header[38] = 't';
            header[39] = 'a';
            header[40] = (byte) (totalAudioLen & 0xff);
            header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
            header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
            header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
            out.write(header, 0, 44);
        }

        public boolean isRecord() {
            return isRecord;
        }

        public int getChannelConfig() {
            return channelconfig;
        }

        public void setChannelConfig(int channelconfig) {
            this.channelconfig = channelconfig;
        }

        public int getAudioSource() {
            return audioSource;
        }

        public void setAudioSource(int audioSource) {
            this.audioSource = audioSource;
        }

        public int getFrequence() {
            return frequence;
        }

        public void setFrequence(int frequence) {
            this.frequence = frequence;
        }

        public int getAudioEncoding() {
            return audioEncoding;
        }

        public void setAudioEncoding(int audioEncoding) {
            this.audioEncoding = audioEncoding;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return AudioFileName;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public long getDurationInMilliseconds() {return timeInMilliseconds;}


}
