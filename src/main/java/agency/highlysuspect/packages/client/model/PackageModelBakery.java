package agency.highlysuspect.packages.client.model;

import agency.highlysuspect.packages.PackagesInit;
import agency.highlysuspect.packages.block.PBlocks;
import agency.highlysuspect.packages.client.compat.frex.FrexCompat;
import agency.highlysuspect.packages.junk.PUtil;
import agency.highlysuspect.packages.junk.PackageStyle;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public record PackageModelBakery(BakedModel baseModel, TextureAtlasSprite specialFrameSprite, TextureAtlasSprite specialInnerSprite) {
	private static final Material SPECIAL_FRAME = new Material(TextureAtlas.LOCATION_BLOCKS, new ResourceLocation(PackagesInit.MODID, "special/frame"));
	private static final Material SPECIAL_INNER = new Material(TextureAtlas.LOCATION_BLOCKS, new ResourceLocation(PackagesInit.MODID, "special/inner"));
	
	public record Spec(ResourceLocation blockModelId) {
		public Collection<ResourceLocation> modelDependencies() {
			return ImmutableList.of(blockModelId);
		}
		
		public Collection<Material> textureDependencies(Function<ResourceLocation, UnbakedModel> unbakedModelGetter, Set<Pair<String, String>> unresolvedTextureReferences) {
			return PUtil.concat(
				unbakedModelGetter.apply(blockModelId).getMaterials(unbakedModelGetter, unresolvedTextureReferences),
				ImmutableList.of(SPECIAL_FRAME, SPECIAL_INNER)
			);
		}
		
		public PackageModelBakery make(ModelBakery loader, Function<Material, TextureAtlasSprite> textureGetter, ModelState rotationContainer, ResourceLocation modelId) {
			return new PackageModelBakery(
				loader.getModel(blockModelId).bake(loader, textureGetter, rotationContainer, modelId),
				textureGetter.apply(SPECIAL_FRAME),
				textureGetter.apply(SPECIAL_INNER)
			);
		}
	}
	
	public Mesh bake(PackageStyle style) {
		return bake(style.color(), style.frameBlock(), style.innerBlock());
	}
	
	public Mesh bake(@Nullable DyeColor faceColor, @Nullable Block frameBlock, @Nullable Block innerBlock) {
		BlockRenderDispatcher mgr = Minecraft.getInstance().getBlockRenderer();
		Renderer renderer = RendererAccess.INSTANCE.getRenderer();
		assert renderer != null;
		
		MeshBuilder meshBuilder = renderer.meshBuilder();
		QuadEmitter emitter = meshBuilder.getEmitter();
		
		Random random = new Random(42);
		
		@Nullable BlockState frameState = frameBlock == null ? null : frameBlock.defaultBlockState();
		@Nullable TextureAtlasSprite frameSprite = frameState == null ? null : mgr.getBlockModel(frameState).getParticleIcon();
		
		@Nullable BlockState innerState = innerBlock == null ? null : innerBlock.defaultBlockState();
		@Nullable TextureAtlasSprite innerSprite = innerState == null ? null : mgr.getBlockModel(innerState).getParticleIcon();
		
		for(Direction cullFace : PUtil.DIRECTIONS_AND_NULL) {
			for(BakedQuad quad : baseModel.getQuads(PBlocks.PACKAGE.defaultBlockState(), cullFace, random)) {
				emitter.fromVanilla(quad, null, cullFace);
				emitter.material(null);
				
				if(emitter.colorIndex() == 1) {
					if(faceColor != null) {
						int tint = 0xFF000000 | faceColor.getMaterialColor().col;
						emitter.spriteColor(0, tint, tint, tint, tint);
						emitter.emit();
					}
					continue;
				}
				
				SpriteUvBounds bounds = SpriteUvBounds.readOff(emitter);
				
				if(bounds.displaysSprite(specialFrameSprite)) {
					if(frameBlock != null) {
						bounds.normalizeEmitter(emitter, specialFrameSprite);
						emitter.spriteBake(0, frameSprite, MutableQuadView.BAKE_NORMALIZED);
						FrexCompat.PROXY.fancifyPackageQuad(emitter, frameState, frameSprite);
						emitter.emit();
					}
					continue;
				}
				
				if(bounds.displaysSprite(specialInnerSprite)) {
					if(innerBlock != null) {
						bounds.normalizeEmitter(emitter, specialInnerSprite);
						emitter.spriteBake(0, innerSprite, MutableQuadView.BAKE_NORMALIZED);
						FrexCompat.PROXY.fancifyPackageQuad(emitter, innerState, innerSprite);
						emitter.emit();
					}
					continue;
				}
				
				//It's not a special quad, so leave it as-is.
				emitter.emit();
			}
		}
		
		return meshBuilder.build();
	}
	
	record SpriteUvBounds(float minU, float maxU, float minV, float maxV) {
		static SpriteUvBounds readOff(QuadEmitter emitter) {
			float minU = Float.POSITIVE_INFINITY;
			float maxU = Float.NEGATIVE_INFINITY;
			float minV = Float.POSITIVE_INFINITY;
			float maxV = Float.NEGATIVE_INFINITY;
			
			for(int i = 0; i < 4; i++) {
				float u = emitter.spriteU(i, 0);
				if(minU > u) minU = u;
				if(maxU < u) maxU = u;
				
				float v = emitter.spriteV(i, 0);
				if(minV > v) minV = v;
				if(maxV < v) maxV = v;
			}
			
			return new SpriteUvBounds(minU, maxU, minV, maxV);
		}
		
		boolean displaysSprite(TextureAtlasSprite sprite) {
			return sprite.getU0() <= minU && sprite.getU1() >= maxU && sprite.getV0() <= minV && sprite.getV1() >= maxV;
		}
		
		void normalizeEmitter(QuadEmitter emitter, TextureAtlasSprite sprite) {
			float remappedMinU = PUtil.rangeRemap(minU, sprite.getU0(), sprite.getU1(), 0, 1);
			float remappedMaxU = PUtil.rangeRemap(maxU, sprite.getU0(), sprite.getU1(), 0, 1);
			float remappedMinV = PUtil.rangeRemap(minV, sprite.getV0(), sprite.getV1(), 0, 1);
			float remappedMaxV = PUtil.rangeRemap(maxV, sprite.getV0(), sprite.getV1(), 0, 1);
			
			//This loop has to go in reverse order or else UV mapping totally falls apart under Canvas (last I checked). Not sure why, I should ask!
			//It's not float comparison issues, pretty sure (if i add an epsilon, it's still broken)
			for(int i = 3; i >= 0; i--) {
				float writeU = emitter.spriteU(i, 0) == minU ? remappedMinU : remappedMaxU;
				float writeV = emitter.spriteV(i, 0) == minV ? remappedMinV : remappedMaxV;
				emitter.sprite(i, 0, writeU, writeV);
			}
		}
	}
}
