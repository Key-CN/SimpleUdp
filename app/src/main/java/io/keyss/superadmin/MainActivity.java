package io.keyss.superadmin;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText et_ip;
    private EditText et_port;
    private EditText et_time;
    private AutoCompleteTextView auto_tv_cmd;
    private RecyclerView rv_receive;
    private Switch switch_monitor;
    private CheckBox cb_auto_send;
    private RadioGroup rg_mode;


    private ArrayList<String> mReceiveDate = new ArrayList<>();
    private ExecutorService executorService;
    private String[] cmds = {"robot_body_advance", "robot_body_retreat", "robot_body_left", "robot_body_right",
            "robot_head_left", "robot_head_right", "robot_body_stop", "_robot_check",
            "robot_si", "robot_sm", "robot_se", "robot_sd", "robot_ipc_adjust",
            "robot_sleep_on", "robot_sleep_off"};
    private boolean isReceive;
    private DatagramPacket mReceivePacket;
    private DatagramSocket mReceiveSocket;
    private Runnable mInsertRunnable;
    private ReceiveAdapter receiveAdapter;
    private SendUdpRunnable mSendUdpRunnable;
    private SendTcpRunnable mSendTcpRunnable;
    private static final int DEFAULT_DELAY_TIME = 300;
    private static final int MODE_UDP = 1;
    private static final int MODE_TCP = 2;


    private void fbc() {
        et_ip = findViewById(R.id.et_ip);
        et_port = findViewById(R.id.et_port);
        et_time = findViewById(R.id.et_time);
        auto_tv_cmd = findViewById(R.id.auto_tv_cmd);
        cb_auto_send = findViewById(R.id.cb_auto_send);
        switch_monitor = findViewById(R.id.switch_monitor);
        rv_receive = findViewById(R.id.rv_receive);
        rg_mode = findViewById(R.id.rg_mode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fbc();
        executorService = Executors.newCachedThreadPool();

        switch_monitor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                rg_mode.setEnabled(isReceive);
                if (isReceive = b) {
                    switch (getMode()) {
                        case MODE_UDP:
                            executorService.execute(new ReceiveUdpRunnable());
                            break;
                        case MODE_TCP:
                            executorService.execute(new ReceiveTcpRunnable());
                            break;
                        default:
                            Toast.makeText(MainActivity.this, "模式错误", Toast.LENGTH_SHORT).show();
                            break;
                    }
                } else {
                    switch (getMode()) {
                        case MODE_UDP:
                            closeUdpReceiveDate();
                            break;
                        case MODE_TCP:

                            break;
                        default:
                            Toast.makeText(MainActivity.this, "模式错误", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }
        });

        // 设置布局管理器，默认垂直
        rv_receive.setLayoutManager(new LinearLayoutManager(this));
        // 设置分隔线
        rv_receive.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        // 设置增加或删除条目的动画
        rv_receive.setItemAnimator(new DefaultItemAnimator());
        // 设置Adapter
        receiveAdapter = new ReceiveAdapter();
        rv_receive.setAdapter(receiveAdapter);
        mReceivePacket = new DatagramPacket(new byte[256], 256);

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, cmds);
        auto_tv_cmd.setAdapter(arrayAdapter);
        auto_tv_cmd.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b) {
                    auto_tv_cmd.showDropDown();
                }
            }
        });

        findViewById(R.id.b_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                auto_tv_cmd.getText().clear();
                auto_tv_cmd.showDropDown();
            }
        });

        findViewById(R.id.b_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(getCmd())) {
                    switch (getMode()) {
                        case MODE_UDP:
                            if (null == mSendUdpRunnable) {
                                mSendUdpRunnable = new SendUdpRunnable();
                            }
                            executorService.execute(mSendUdpRunnable);
                            break;
                        case MODE_TCP:
                            if (null == mSendTcpRunnable) {
                                mSendTcpRunnable = new SendTcpRunnable();
                            }
                            executorService.execute(mSendTcpRunnable);
                            break;
                        default:
                            break;
                    }
                } else {
                    Toast.makeText(MainActivity.this, "消息不可以为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    private int getMode() {
        switch (rg_mode.getCheckedRadioButtonId()) {
            case R.id.rb_udp:
                return MODE_UDP;
            case R.id.rb_tcp:
                return MODE_TCP;
            default:
                return 0;
        }
    }

    private void InsertItem() {
        if (null == mInsertRunnable) {
            mInsertRunnable = new Runnable() {
                @Override
                public void run() {
                    receiveAdapter.notifyItemInserted(mReceiveDate.size() - 1);
                    rv_receive.scrollToPosition(mReceiveDate.size() - 1);
                }
            };
        }
        runOnUiThread(mInsertRunnable);
    }

    private int getPort() {
        try {
            Integer port = Integer.valueOf(et_port.getText().toString().trim());
            if (port > 65535) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        et_port.setText("9999");
                        Toast.makeText(MainActivity.this, "端口不能大于65535", Toast.LENGTH_SHORT).show();
                    }
                });
                return 9999;
            } else {
                return port;
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    et_port.setText("9999");
                    Toast.makeText(MainActivity.this, "端口错误", Toast.LENGTH_SHORT).show();
                }
            });
            return 9999;
        }
    }

    private int getDelayTime() {
        try {
            return Integer.valueOf(et_time.getText().toString().trim());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            if (mReceiveDate.add("时间格式错误，自动设为300ms")) {
                InsertItem();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cb_auto_send.setChecked(false);
                }
            });
            return DEFAULT_DELAY_TIME;
        }
    }

    private String getIp() {
        return et_ip.getText().toString().trim();
    }

    private String getCmd() {
        return auto_tv_cmd.getText().toString().trim();
    }

    private void sendCmd(InetSocketAddress address, DatagramSocket socket) throws IOException {
        String cmd = getCmd();
        byte[] cmdBytes = cmd.getBytes();
        socket.send(new DatagramPacket(cmdBytes, cmdBytes.length, address));
        if (mReceiveDate.add("发  时间: " + System.currentTimeMillis() + "\n消息: " + cmd)) {
            InsertItem();
        }
        if (cb_auto_send.isChecked()) {
            SystemClock.sleep(getDelayTime());
            sendCmd(address, socket);
        }
    }

    private void closeUdpReceiveDate() {
        if (null != mReceiveSocket) {
            if (!mReceiveSocket.isClosed()) {
                mReceiveSocket.close();
            }
            mReceiveSocket.disconnect();
            mReceiveSocket = null;
        }
    }

    class SendTcpRunnable implements Runnable {

        @Override
        public void run() {
            try {
                Socket s = new Socket(getIp(), getPort());
                // outgoing stream redirect to socket
                OutputStream out = s.getOutputStream();
                // 注意第二个参数据为true将会自动flush，否则需要需要手动操作out.flush()
                PrintWriter output = new PrintWriter(out, true);
                output.println(getCmd());
                BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
                // read line(s)
                String message = input.readLine();
                Log.e("Tcp Demo", "message From Server:" + message);
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ReceiveTcpRunnable implements Runnable {

        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "开始监听" + getPort() + "端口TCP", Toast.LENGTH_SHORT).show();
                }
            });
            try {
                Boolean endFlag = false;
                ServerSocket ss = new ServerSocket(getPort());
                while (!endFlag) {
                    // 等待客户端连接
                    Socket s = ss.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    //注意第二个参数据为true将会自动flush，否则需要需要手动操作output.flush()
                    PrintWriter output = new PrintWriter(s.getOutputStream(), true);
                    String message = input.readLine();
                    Log.e("Tcp Demo", "message from Client:" + message);
                    output.println("message received!");
                    //output.flush();
                    if ("shutDown".equals(message)) {
                        endFlag = true;
                    }
                    s.close();
                }
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class SendUdpRunnable implements Runnable {
        @Override
        public void run() {
            InetSocketAddress address = new InetSocketAddress(getIp(), getPort());
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(getPort()));

                sendCmd(address, socket);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != socket) {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                    socket.disconnect();
                }
            }
        }
    }

    class ReceiveUdpRunnable implements Runnable {
        @Override
        public void run() {
            while (isReceive) {
                closeUdpReceiveDate();
                try {
                    mReceiveSocket = new DatagramSocket(null);
                    mReceiveSocket.setReuseAddress(true);
                    mReceiveSocket.bind(new InetSocketAddress(getPort()));
                    mReceiveSocket.receive(mReceivePacket);
                    String result = new String(mReceivePacket.getData(), mReceivePacket.getOffset(), mReceivePacket.getLength());
                    if (mReceiveDate.add("收  时间: " + System.currentTimeMillis() + "\n消息: " + result)) {
                        InsertItem();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    closeUdpReceiveDate();
                }
            }
        }
    }

    class ReceiveAdapter extends RecyclerView.Adapter<ReceiveAdapter.ReceiveHolder> {

        @NonNull
        @Override
        public ReceiveHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ReceiveHolder(View.inflate(parent.getContext(), android.R.layout.simple_list_item_1, null));
        }

        @Override
        public void onBindViewHolder(@NonNull ReceiveHolder holder, int position) {
            holder.text.setText(mReceiveDate.get(position));
        }

        @Override
        public int getItemCount() {
            return mReceiveDate.size();
        }

        class ReceiveHolder extends RecyclerView.ViewHolder {

            private final TextView text;

            ReceiveHolder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
