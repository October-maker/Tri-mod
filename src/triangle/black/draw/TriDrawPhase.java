package triangle.black.draw;

/**
 * 绘制相位：决定一个 {@link TriDrawLayer} 在 {@link TriDrawChain} 里被执行的时机。
 *
 * <p>顺序：{@link #UNDER} → 原版 DrawBlock 组合 → {@link #BODY} → {@link #OVERLAY}。
 * {@link #LIGHT} 只在 {@code drawLight()} 中执行，{@link #PLAN} 只在蓝图预览中执行。
 */
public enum TriDrawPhase{
    /** 原版主体之前（底座、阴影、液体底层等）。 */
    UNDER,
    /** 原版主体之后、叠加层之前（主体特效、与主体同层的附加贴图）。 */
    BODY,
    /** 最上层（状态标记、读数、警示等）。 */
    OVERLAY,
    /** 只参与 {@code drawLight()} 的发光层。 */
    LIGHT,
    /** 只参与蓝图预览的层。 */
    PLAN
}
