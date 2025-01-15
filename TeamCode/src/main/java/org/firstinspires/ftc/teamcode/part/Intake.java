package org.firstinspires.ftc.teamcode.part;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.CRServo;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.feature.Schedule;
import org.firstinspires.ftc.teamcode.feature.Vision;
import org.firstinspires.ftc.teamcode.global.Global;

@Config
class IntakeConstants {
    private IntakeConstants() {} // Prevent instantiation

    // Horizontal Linear
    public static final double HOR_LINEAR_INNER_POSE = 0.0;
    public static final double HOR_LINEAR_OUTER_POSE = 0.0;

    public enum HorLinearMode { MANUAL, AUTO, EMERGENCY }
    public static HorLinearMode HOR_LINEAR_MODE = HorLinearMode.AUTO;

    public static final double HOR_LINEAR_AUTO_SPEED = 0.6;
    public static final double HOR_LINEAR_MANUAL_SPEED = 0.3;

    public static final double HOR_LINEAR_kP = 1.0 * 0.001;

    // Eater
    public static final double EATER_ARM_DOWN_POSE = 0.0;
    public static final double EATER_ARM_NEUTRAL_POSE = 0.5;
    public static final double EATER_ARM_UP_POSE = 1.0;

    public static final double EATER_HAND_DOWN_POSE = 0.0;
    public static final double EATER_HAND_NEUTRAL_POSE = 0.5;
    public static final double EATER_HAND_UP_POSE = 1.0;

    public static final double EATER_ANGLE_UP = 0.5;
    public static final double EATER_ANGLE_SPECIMEN = 1.0;
    public static final double EATER_ANGLE_SAMPLE = 0.0;

    public static final double EATER_SPEED = 0.5;

    public static final double EATER_HAND_SPEED = 0.02;

    //DELAY
    public static final long DELAY_LINEAR_RETRACT = 100;
    public static final long DELAY_ARM_UP = 100;
}

// Main Part
public class Intake implements Part{
    private static final HorizontalLinear horizontalLinear = new HorizontalLinear();
    private static final Eater eater = new Eater();

    private static int currentStep = 3;
    private static int previousStep = 3;
    private int[] nextStep = {1, 3, 1, 0};
    private int[] backStep = {0, 2, 0, 3};

    private Vision.SampleColor targetColor = Vision.SampleColor.YELLOW;

    public void init(HardwareMap hardwareMap, Telemetry telemetry) {
        horizontalLinear.init(hardwareMap, telemetry);
        eater.init(hardwareMap, telemetry);
    }

    public void update() {
        horizontalLinear.update();
        eater.update();
    }

    public void stop() {
        horizontalLinear.stop();
        eater.stop();
    }

    public void cmdIntakeSample() {
        eater.cmdArmDownForSample();
        eater.cmdEaterRun(true);
    }
    public void cmdIntakeSpecimen() {
        eater.cmdArmDownForSpecimen();
        eater.cmdEaterRun(true);
    }
    public void cmdIntakeFreeAngle() {
        eater.cmdArmDownForFreeAngle();
        eater.cmdEaterRun(true);
    }
    public void cmdIntakeVomit() {
        eater.cmdEaterRun(false);
        eater.cmdArmNeutral();
    }
    public void cmdAutoStretch() {
        Global.robotState = Global.RobotState.INTAKE;

        horizontalLinear.cmdSetMode(IntakeConstants.HorLinearMode.AUTO);
        horizontalLinear.cmdStretch();
    }
    public void cmdAutoRetract() {
        Global.robotState = Global.RobotState.NONE;
        horizontalLinear.cmdSetMode(IntakeConstants.HorLinearMode.AUTO);

        eater.cmdEaterStop();

        Schedule.addTask(horizontalLinear::cmdRetract, IntakeConstants.DELAY_LINEAR_RETRACT);
        Schedule.addTask(eater::cmdArmUp, IntakeConstants.DELAY_ARM_UP);
    }
    public void cmdAutoRotate() {
        eater.cmdHandAutoRotate();
    }
    public void cmdManualStretch() {
        horizontalLinear.cmdSetMode(IntakeConstants.HorLinearMode.MANUAL);
        horizontalLinear.cmdStretch();
    }
    public void cmdManualRetract() {
        horizontalLinear.cmdSetMode(IntakeConstants.HorLinearMode.MANUAL);
        horizontalLinear.cmdRetract();
    }
    public void cmdManualStop() {
        horizontalLinear.cmdManualStop();
    }
    public void cmdManualRotate(int direction) {
        eater.cmdHandManualRotate(direction);
    }

    public void setTargetColor(Vision.SampleColor color){
        targetColor = color;
    }

}

// Sub Part
class HorizontalLinear implements Part {

    private DcMotor motor;
    private double targetPosition = IntakeConstants.HOR_LINEAR_INNER_POSE;
    private boolean isUsingPID = false;
    private boolean isBusy = false;

    public void init(HardwareMap hardwareMap, Telemetry telemetry) {
        motor = hardwareMap.get(DcMotor.class, "horizontalLinear");

        motor.setDirection(DcMotorSimple.Direction.FORWARD);
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    public void update() {
        if (this.isUsingPID) {
            double err = targetPosition - motor.getCurrentPosition();
            double power = IntakeConstants.HOR_LINEAR_kP * err;
            if (IntakeConstants.HOR_LINEAR_MODE == IntakeConstants.HorLinearMode.AUTO) {
                if (power > 0) power = Math.min(power, IntakeConstants.HOR_LINEAR_AUTO_SPEED);
                else power = Math.max(power, -IntakeConstants.HOR_LINEAR_AUTO_SPEED);
            } else {
                if (power > 0) power = Math.min(power, IntakeConstants.HOR_LINEAR_MANUAL_SPEED);
                else power = Math.max(power, -IntakeConstants.HOR_LINEAR_MANUAL_SPEED);
            }
            motor.setPower(power);
        }
    }

    public void stop() {
        IntakeConstants.HOR_LINEAR_MODE = IntakeConstants.HorLinearMode.EMERGENCY;
        this.targetPosition = motor.getCurrentPosition();
        this.isUsingPID = false;
        motor.setPower(0);
    }

    public void cmdSetMode(IntakeConstants.HorLinearMode mode) {
        IntakeConstants.HOR_LINEAR_MODE = mode;
        motor.setPower(0);
        if (mode == IntakeConstants.HorLinearMode.AUTO) {
            this.targetPosition = IntakeConstants.HOR_LINEAR_INNER_POSE;
        } else {
            this.targetPosition = motor.getCurrentPosition();
        }
        this.isUsingPID = true;
    }
    public void cmdStretch() {
        if (IntakeConstants.HOR_LINEAR_MODE == IntakeConstants.HorLinearMode.AUTO) {
            this.targetPosition = IntakeConstants.HOR_LINEAR_OUTER_POSE;
            this.isUsingPID = true;
        } else {
            this.isUsingPID = false;
            motor.setPower(IntakeConstants.HOR_LINEAR_MANUAL_SPEED);
        }
    }
    public void cmdRetract() {
        if (IntakeConstants.HOR_LINEAR_MODE == IntakeConstants.HorLinearMode.AUTO) {
            this.targetPosition = IntakeConstants.HOR_LINEAR_INNER_POSE;
            this.isUsingPID = true;
        } else {
            this.isUsingPID = false;
            motor.setPower(-IntakeConstants.HOR_LINEAR_MANUAL_SPEED);
        }
    }
    public void cmdManualStop() {
        if (IntakeConstants.HOR_LINEAR_MODE == IntakeConstants.HorLinearMode.MANUAL) {
            this.isUsingPID = true;
            this.targetPosition = motor.getCurrentPosition();
            motor.setPower(0);
        } else if (IntakeConstants.HOR_LINEAR_MODE == IntakeConstants.HorLinearMode.EMERGENCY) {
            this.isUsingPID = false;
            this.targetPosition = motor.getCurrentPosition();
            motor.setPower(0);
        }
    }
    public void setBusy(boolean busy){
        isBusy = busy;
    }
    public boolean isBusy(){
        return isBusy;
    }
}

// Sub Part
class Eater implements Part {

    private Servo armServo1, armServo2;
    private Servo handServo, handRotationServo;
    private CRServo eaterServo;
    private boolean isBusy;

    private double targetAngle;


    public void init(HardwareMap hardwareMap, Telemetry telemetry) {
        armServo1 = hardwareMap.get(Servo.class, "intakeArm1");
        armServo2 = hardwareMap.get(Servo.class, "intakeArm2");
        handServo = hardwareMap.get(Servo.class, "intakeHand");
        handRotationServo = hardwareMap.get(Servo.class, "intakeRotation");
        eaterServo = hardwareMap.get(CRServo.class, "intakeEater");
    }

    public void update() {
    }

    public void stop() {
        cmdEaterStop();
        cmdHandManualRotate(0);
    }

    public void cmdEaterRun(boolean inside){
        eaterServo.setPower(inside ? IntakeConstants.EATER_SPEED : -IntakeConstants.EATER_SPEED);
    }
    public void cmdEaterStop(){
        eaterServo.setPower(0);
    }
    public void cmdHandAutoRotate(){
        Vision.Sample target = Vision.detectTarget();
        if (target != null){
            targetAngle = target.angle;
        }
        handRotationServo.setPosition(targetAngle);
    }
    public void cmdHandManualRotate(int direction){
        targetAngle += direction * IntakeConstants.EATER_HAND_SPEED;

        if (targetAngle < 0) targetAngle = 0;
        else if (targetAngle > 1) targetAngle = 1;

        handRotationServo.setPosition(targetAngle);
    }
    public void cmdArmUp(){
        targetAngle = IntakeConstants.EATER_ANGLE_UP;
        handRotationServo.setPosition(IntakeConstants.EATER_ANGLE_UP);

        handServo.setPosition(IntakeConstants.EATER_HAND_UP_POSE);

        armServo1.setPosition(IntakeConstants.EATER_ARM_UP_POSE);
        armServo2.setPosition(IntakeConstants.EATER_ARM_UP_POSE);
    }
    public void cmdArmNeutral(){
        targetAngle = IntakeConstants.EATER_ANGLE_UP;
        handRotationServo.setPosition(targetAngle);

        handServo.setPosition(IntakeConstants.EATER_HAND_NEUTRAL_POSE);

        armServo1.setPosition(IntakeConstants.EATER_ARM_NEUTRAL_POSE);
        armServo2.setPosition(IntakeConstants.EATER_ARM_NEUTRAL_POSE);
    }
    public void cmdArmDownForSample(){
        targetAngle = IntakeConstants.EATER_ANGLE_SAMPLE;
        handRotationServo.setPosition(targetAngle);

        handServo.setPosition(IntakeConstants.EATER_HAND_DOWN_POSE);

        armServo1.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
        armServo2.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
    }
    public void cmdArmDownForSpecimen(){
        targetAngle = IntakeConstants.EATER_ANGLE_SPECIMEN;
        handRotationServo.setPosition(targetAngle);

        handServo.setPosition(IntakeConstants.EATER_HAND_DOWN_POSE);

        armServo1.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
        armServo2.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
    }
    public void cmdArmDownForFreeAngle(){
        handRotationServo.setPosition(targetAngle);

        handServo.setPosition(IntakeConstants.EATER_HAND_DOWN_POSE);

        armServo1.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
        armServo2.setPosition(IntakeConstants.EATER_ARM_DOWN_POSE);
    }
    public void setBusy(boolean busy){
        isBusy = busy;
    }
    public boolean isBusy(){
        return isBusy;
    }
}