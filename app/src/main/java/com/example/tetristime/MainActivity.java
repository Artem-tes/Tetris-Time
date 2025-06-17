package com.example.tetristime;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 18;
    private static final int CELL_SIZE_DP = 40;
    private static final int GRAVITY_DELAY = 900;
    private static final String PREFS_NAME = "TetrisTasks";
    private static final String TASK_PREFIX = "task_";

    private GameView gameView;
    private Handler handler;
    private Runnable gravityRunnable;
    private int tasksCompleted = 0;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        TextView scoreText = findViewById(R.id.scoreText);
        FrameLayout gameContainer = findViewById(R.id.gameContainer);

        gameView = new GameView(this);
        gameContainer.addView(gameView);

        handler = new Handler();
        setupButtons();
    }

    private void setupButtons() {
        Button left = findViewById(R.id.buttonLeft);
        Button right = findViewById(R.id.buttonRight);
        Button down = findViewById(R.id.buttonDown);
        Button rotate = findViewById(R.id.buttonRotate);
        Button addTask = findViewById(R.id.buttonAddTask);

        left.setOnClickListener(v -> gameView.moveLeft());
        right.setOnClickListener(v -> gameView.moveRight());
        down.setOnClickListener(v -> gameView.moveDown());
        rotate.setOnClickListener(v -> gameView.rotateShape());
        addTask.setOnClickListener(v -> showAddTaskDialog());
    }

    private void showAddTaskDialog() {
        EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Add Task")
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    String task = input.getText().toString();
                    if (!task.isEmpty()) {
                        gameView.spawnNewShape(task);
                        startGravity();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startGravity() {
        if (gravityRunnable != null) {
            handler.removeCallbacks(gravityRunnable);
        }

        gravityRunnable = new Runnable() {
            @Override
            public void run() {
                if (gameView.moveDown()) {
                    handler.postDelayed(this, GRAVITY_DELAY);
                } else {
                    tasksCompleted++;
                    ((TextView)findViewById(R.id.scoreText)).setText("Completed: " + tasksCompleted);
                }
            }
        };
        handler.postDelayed(gravityRunnable, GRAVITY_DELAY);
    }

    private class GameView extends View {
        private float cellSize;
        private Paint cellPaint, gridPaint;
        private int[][] currentShape;
        private int currentX, currentY;
        private String currentTask;
        private BlockType currentType;
        private List<Shape> fixedShapes = new ArrayList<>();

        public GameView(Context context) {
            super(context);
            init();
        }

        private void init() {
            cellPaint = new Paint();
            gridPaint = new Paint();
            gridPaint.setColor(Color.GRAY);
            gridPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            cellSize = Math.min(width / (float) GRID_WIDTH, height / (float) GRID_HEIGHT);
            int desiredWidth = (int) (GRID_WIDTH * cellSize);
            int desiredHeight = (int) (GRID_HEIGHT * cellSize);
            setMeasuredDimension(desiredWidth, desiredHeight);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawGrid(canvas);
            drawFixedShapes(canvas);
            drawCurrentShape(canvas);
        }

        private void drawGrid(Canvas canvas) {
            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#212121"));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4f);
            canvas.drawRect(0, 0, GRID_WIDTH * cellSize, GRID_HEIGHT * cellSize, borderPaint);

            gridPaint.setColor(Color.parseColor("#BDBDBD"));
            for (int i = 0; i <= GRID_WIDTH; i++) {
                float x = i * cellSize;
                canvas.drawLine(x, 0, x, GRID_HEIGHT * cellSize, gridPaint);
            }
            for (int i = 0; i <= GRID_HEIGHT; i++) {
                float y = i * cellSize;
                canvas.drawLine(0, y, GRID_WIDTH * cellSize, y, gridPaint);
            }
        }

        private void drawCurrentShape(Canvas canvas) {
            if (currentShape == null) return;
            cellPaint.setColor(currentType.color);
            drawShape(canvas, currentShape, currentX, currentY);
        }

        private void drawFixedShapes(Canvas canvas) {
            for (Shape shape : fixedShapes) {
                cellPaint.setColor(shape.type.color);
                drawShape(canvas, shape.shape, shape.x, shape.y);
            }
        }

        private void drawShape(Canvas canvas, int[][] shape, int x, int y) {
            for (int i = 0; i < shape.length; i++) {
                for (int j = 0; j < shape[i].length; j++) {
                    if (shape[i][j] == 1) {
                        float left = (x + j) * cellSize;
                        float top = (y + i) * cellSize;
                        canvas.drawRect(left, top, left + cellSize, top + cellSize, cellPaint);
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int x = (int)(event.getX() / cellSize);
                int y = (int)(event.getY() / cellSize);
                showTaskDialog(x, y);
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void showTaskDialog(int x, int y) {
            for (int i = 0; i < fixedShapes.size(); i++) {
                Shape shape = fixedShapes.get(i);
                if (shape.contains(x, y)) {
                    String task = prefs.getString(TASK_PREFIX + shape.id, "No description");
                    int finalI = i;
                    new AlertDialog.Builder(getContext())
                            .setTitle("Task Details")
                            .setMessage(task)
                            .setPositiveButton("Delete", (dialog, which) -> {
                                fixedShapes.remove(finalI);
                                removeTaskFromPrefs(shape.id);
                                invalidate();
                                checkFullRows();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }
        }

        private void removeTaskFromPrefs(String id) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(TASK_PREFIX + id);
            editor.apply();
        }

        private void checkFullRows() {
            for (int y = GRID_HEIGHT - 1; y >= 0; y--) {
                if (isRowFull(y)) {
                    removeRow(y);
                    y++; // Check same row again
                }
            }
        }

        private boolean isRowFull(int y) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (!isCellOccupied(x, y)) return false;
            }
            return true;
        }

        private void removeRow(int y) {
            List<Shape> shapesToRemove = new ArrayList<>();
            for (Shape shape : fixedShapes) {
                if (shape.isInRow(y)) {
                    shapesToRemove.add(shape);
                }
            }
            for (Shape shape : shapesToRemove) {
                fixedShapes.remove(shape);
                removeTaskFromPrefs(shape.id);
            }
            for (Shape shape : fixedShapes) {
                if (shape.y < y) {
                    shape.y++;
                }
            }
            invalidate();
        }

        public void spawnNewShape(String task) {
            currentType = BlockType.getRandomType();
            currentShape = currentType.shape;
            currentX = GRID_WIDTH / 2 - currentShape[0].length / 2;
            currentY = 0;
            currentTask = task;

            if (!canPlace(currentShape, currentX, currentY)) {
                currentShape = null;
                return;
            }

            invalidate();
        }

        public void moveLeft() {
            if (canPlace(currentShape, currentX - 1, currentY)) {
                currentX--;
                invalidate();
            }
        }

        public void moveRight() {
            if (canPlace(currentShape, currentX + 1, currentY)) {
                currentX++;
                invalidate();
            }
        }

        public boolean moveDown() {
            if (currentY + currentShape.length >= GRID_HEIGHT) {
                fixShape();
                return false;
            }
            if (canPlace(currentShape, currentX, currentY + 1)) {
                currentY++;
                invalidate();
                return true;
            } else {
                fixShape();
                return false;
            }
        }

        public void rotateShape() {
            int[][] rotated = rotateMatrix(currentShape);
            if (canPlace(rotated, currentX, currentY)) {
                currentShape = rotated;
                invalidate();
            }
        }

        private int[][] rotateMatrix(int[][] matrix) {
            int[][] rotated = new int[matrix[0].length][matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    rotated[j][matrix.length - 1 - i] = matrix[i][j];
                }
            }
            return rotated;
        }

        private void fixShape() {
            if (currentShape == null) return;
            String shapeId = UUID.randomUUID().toString();
            fixedShapes.add(new Shape(currentShape.clone(), currentX, currentY, currentTask, currentType, shapeId));
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(TASK_PREFIX + shapeId, currentTask);
            editor.apply();
            currentShape = null;
            checkFullRows();
        }

        private boolean canPlace(int[][] shape, int x, int y) {
            if (shape == null) return false;

            for (int i = 0; i < shape.length; i++) {
                for (int j = 0; j < shape[i].length; j++) {
                    if (shape[i][j] == 1) {
                        int newX = x + j;
                        int newY = y + i;

                        if (newX < 0 || newX >= GRID_WIDTH || newY >= GRID_HEIGHT) {
                            return false;
                        }

                        if (newY >= 0 && isCellOccupied(newX, newY)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private boolean isCellOccupied(int x, int y) {
            for (Shape shape : fixedShapes) {
                if (shape.contains(x, y)) return true;
            }
            return false;
        }
    }

    private static class Shape {
        int[][] shape;
        int x, y;
        String task;
        BlockType type;
        String id;

        Shape(int[][] shape, int x, int y, String task, BlockType type, String id) {
            this.shape = shape;
            this.x = x;
            this.y = y;
            this.task = task;
            this.type = type;
            this.id = id;
        }

        boolean contains(int cellX, int cellY) {
            for (int i = 0; i < shape.length; i++) {
                for (int j = 0; j < shape[i].length; j++) {
                    if (shape[i][j] == 1 && x + j == cellX && y + i == cellY) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean isInRow(int row) {
            for (int i = 0; i < shape.length; i++) {
                for (int j = 0; j < shape[i].length; j++) {
                    if (shape[i][j] == 1 && y + i == row) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private enum BlockType {
        I(new int[][]{{1, 1, 1, 1}}, Color.RED),
        O(new int[][]{{1, 1}, {1, 1}}, Color.BLUE),
        T(new int[][]{{0, 1, 0}, {1, 1, 1}}, Color.GREEN),
        S(new int[][]{{0, 1, 1}, {1, 1, 0}}, Color.YELLOW),
        Z(new int[][]{{1, 1, 0}, {0, 1, 1}}, Color.MAGENTA),
        J(new int[][]{{1, 0, 0}, {1, 1, 1}}, Color.CYAN),
        L(new int[][]{{0, 0, 1}, {1, 1, 1}}, Color.GRAY);

        final int[][] shape;
        final int color;

        BlockType(int[][] shape, int color) {
            this.shape = shape;
            this.color = color;
        }

        static BlockType getRandomType() {
            return values()[new Random().nextInt(values().length)];
        }
    }
}