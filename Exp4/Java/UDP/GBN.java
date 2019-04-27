package UDP;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class GBN {

    private static int myport;
    private static int hisport;
    private static int FilterError;
    private static int FilterLost;
    private static DatagramSocket datagramSocket;
    private static InetAddress inetAddress;
    private static String[] datasend= {"00000","1010010","1000000","1010101010","00001","10101010","101010","101010101010"};

    private static int buffered=0;
    private static int next_frame_send=0;
    private static int frame_expected=0;
    private static int ack_expected=0;
    private static int max_seq=5;
    private static boolean time_out=false;
    private static long timelimit=2000;
    private static Queue<Timer> timer_queue = new LinkedList<>();

    private static class SendThread implements Runnable {

        @Override
        public synchronized void run() {
            System.out.println("sending");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while(true) {
                if(time_out) {
                   // int a=timer_queue.peek().frame_num;
                    System.out.println("time_out!");
                    int tmpbuf=buffered;
                    int tmpack=ack_expected;
                    for(int i=0; i < tmpbuf; i++) {
                        String data;
                        data=datasend[tmpack+i]+"/"+(tmpack+i)+"/"+(frame_expected-1);
                        byte[] sendData=data.getBytes();

                        DatagramPacket datagramPacketsend = new DatagramPacket(sendData,sendData.length,inetAddress,hisport);
                        try {
                            datagramSocket.send(datagramPacketsend);
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        System.out.println("resend:"+data);
                        stop_timer(tmpack+i);
                        start_timer(tmpack+i);

                    }
                    time_out=false;
                }
                else {
                    if(buffered<max_seq) {
                        //System.out.println(buffered+"\n");
                        String data;
                        if(next_frame_send>7){
                            next_frame_send=7;
                        }

                        if (next_frame_send == 5) {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if(frame_expected==8&&ack_expected==8){
                            break;
                        }
                        data=datasend[next_frame_send]+"/"+next_frame_send+"/"+(frame_expected-1);
                        byte[] sendData=data.getBytes();

                        DatagramPacket datagramPacketsend = new DatagramPacket(sendData,sendData.length,inetAddress,hisport);
                        try {
                            datagramSocket.send(datagramPacketsend);
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        System.out.println("send:"+data);
                        start_timer(next_frame_send);
                        buffered++;
                        next_frame_send++;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else{
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                       // System.out.println("buffered max");
                        continue;
                    }
                }
            }
           System.out.println("sending finish");
        }

    }

    private static class ReceiveThread implements Runnable {

        @Override
        public synchronized void run() {
            System.out.println("receiving");
            while(true) {

                byte[] receiveData=new byte[1024];
                DatagramPacket datagramPacketreceive=new DatagramPacket(receiveData,receiveData.length);
                try {
                    datagramSocket.receive(datagramPacketreceive);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                String data=new String(datagramPacketreceive.getData());

                System.out.println("receive:"+data);

                String[] info=data.trim().split("/");
                int receiveframe=Integer.parseInt(info[1]);
                int receiveack;
                if(info[2]=="-1"){
                    receiveack=-1;
                }
                else{
                    receiveack= Integer.parseInt(info[2]);
                }

                if(receiveframe==frame_expected) {
                    boolean crcflag=true;
                    if(crcflag) {
                        frame_expected++;
                       // System.out.println("expect data");
                    }

                }

                if(receiveack>=ack_expected) {
                    for(int i=ack_expected;i<=receiveack;i++) {
                       // System.out.println(i+"comfirm\n");
                        stop_timer(i);
                        buffered--;

                    }
                    ack_expected=receiveack+1;
                }
                if(frame_expected==8&&ack_expected==8){
                    break;
                }
            }
            System.out.println("receiving finish");
        }

    }

    private static void stop_timer(int frame) {
        int number= 0;
        if (timer_queue.peek() != null) {
            number = timer_queue.peek().frame_num;
        }
        if(number==frame) {
            timer_queue.poll();
        }
    }

    private static void start_timer(int frame) {
        Timer newtimer = new Timer();
        newtimer.frame_num=frame;
        newtimer.start_time= System.currentTimeMillis();
        timer_queue.add(newtimer);
    }

    private static class TimerThread implements Runnable{
        @Override
        public synchronized void run() {
            while(true) {
               // System.out.println("timer");
                if(!time_out) {
                    if(timer_queue.peek() == null){
                        //System.out.println("queue empty!");
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else if (timer_queue.peek() != null) {
                        long nowtime=System.currentTimeMillis();
                        long toptime= 0;
                        toptime = timer_queue.peek().start_time;

                        long dif = nowtime - toptime;
                       // System.out.println(dif + "ms");
                        if (dif >= timelimit) {
                            time_out = true;
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if(frame_expected==8&&ack_expected==8){
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        //Queue<Timer> timer_queue = new LinkedList<Timer>();
        myport = 8888;
        hisport = 7777;
        FilterError = FilterLost = 10;
        datagramSocket = new DatagramSocket(myport);
        inetAddress = InetAddress.getLocalHost();

        new Thread(new TimerThread()).start();
        new Thread(new SendThread()).start();
        new Thread(new ReceiveThread()).start();
    }
}

class Timer{
    int frame_num;
    long start_time;
}
