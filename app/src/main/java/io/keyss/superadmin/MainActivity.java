package io.keyss.superadmin;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
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


    private ArrayList<String> mReceiveDate = new ArrayList<>();
    private ExecutorService executorService;
    private String[] cmds = {"robot_body_advance", "robot_body_retreat", "robot_body_left", "robot_body_right",
            "robot_head_left", "robot_head_right", "robot_body_stop", "_robot_check"};
    private boolean isReceive;
    private long resendTime = 100;
    private DatagramPacket mReceivePacket;
    private DatagramSocket mReceiveSocket;
    private Runnable mInsertRunnable;
    private ReceiveAdapter receiveAdapter;


    private void fbc() {
        et_ip = findViewById(R.id.et_ip);
        et_port = findViewById(R.id.et_port);
        et_time = findViewById(R.id.et_time);
        auto_tv_cmd = findViewById(R.id.auto_tv_cmd);
        cb_auto_send = findViewById(R.id.cb_auto_send);
        switch_monitor = findViewById(R.id.switch_monitor);
        rv_receive = findViewById(R.id.rv_receive);
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
                if (isReceive = b) {
                    executorService.execute(new ReceiveRunnable());
                } else {
                    closeReceiveDate();
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
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            InetSocketAddress address = new InetSocketAddress(getIp(), getPort());
                            DatagramSocket socket = null;
                            try {
                                socket = new DatagramSocket(null);
                                socket.setReuseAddress(true);
                                socket.bind(new InetSocketAddress(getPort()));

                                String cmd = getCmd();
                                byte[] cmdBytes = cmd.getBytes();
                                socket.send(new DatagramPacket(cmdBytes, cmdBytes.length, address));

                                if (mReceiveDate.add("发  时间: " + System.currentTimeMillis() + "\n消息: " + cmd)) {
                                    InsertItem();
                                }
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
                    });
                } else {
                    Toast.makeText(MainActivity.this, "消息不可以为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

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
            return Integer.valueOf(et_port.getText().toString().trim());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            Toast.makeText(this, "端口错误", Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private String getIp() {
        return et_ip.getText().toString().trim();
    }

    private String getCmd() {
        return auto_tv_cmd.getText().toString().trim();
    }

    private void closeReceiveDate() {
        if (null != mReceiveSocket) {
            if (!mReceiveSocket.isClosed()) {
                mReceiveSocket.close();
            }
            mReceiveSocket.disconnect();
            mReceiveSocket = null;
        }
    }

    class ReceiveRunnable implements Runnable {
        @Override
        public void run() {
            while (isReceive) {
                closeReceiveDate();
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
                    closeReceiveDate();
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
