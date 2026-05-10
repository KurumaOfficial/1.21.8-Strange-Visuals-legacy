package ru.strange.client.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.strange.client.module.impl.other.NameProtect;

@Mixin(TextRenderer.class)
public class TextRendererMixin {

    @ModifyVariable(method = "mirror(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true)
    private String strange$protectMirror(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true)
    private String strange$protectDrawString(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true)
    private Text strange$protectDrawText(Text text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true)
    private OrderedText strange$protectDrawOrderedText(OrderedText text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), argsOnly = true)
    private OrderedText strange$protectDrawOutline(OrderedText text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String strange$protectStringWidth(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true)
    private StringVisitable strange$protectTextWidth(StringVisitable text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true)
    private OrderedText strange$protectOrderedWidth(OrderedText text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true)
    private String strange$protectTrimWithReverse(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true)
    private String strange$protectTrim(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "trimToWidth(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/StringVisitable;", at = @At("HEAD"), argsOnly = true)
    private StringVisitable strange$protectTrimVisitable(StringVisitable text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "getWrappedLinesHeight(Ljava/lang/String;I)I", at = @At("HEAD"), argsOnly = true)
    private String strange$protectWrappedHeight(String text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "getWrappedLinesHeight(Lnet/minecraft/text/StringVisitable;I)I", at = @At("HEAD"), argsOnly = true)
    private StringVisitable strange$protectWrappedHeightVisitable(StringVisitable text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;", at = @At("HEAD"), argsOnly = true)
    private StringVisitable strange$protectWrapLines(StringVisitable text) {
        return NameProtect.process(text);
    }

    @ModifyVariable(method = "wrapLinesWithoutLanguage(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;", at = @At("HEAD"), argsOnly = true)
    private StringVisitable strange$protectWrapLinesRaw(StringVisitable text) {
        return NameProtect.process(text);
    }
}