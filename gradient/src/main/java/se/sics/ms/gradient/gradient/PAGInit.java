package se.sics.ms.gradient.gradient;

import se.sics.kompics.Init;
import se.sics.p2ptoolbox.gradient.GradientConfig;
import se.sics.p2ptoolbox.util.config.SystemConfig;

/**
 * Initializer for the Partition Aware Gradient Component.
 *  
 * Created by babbarshaer on 2015-06-03.
 */
public class PAGInit extends Init<PartitionAwareGradient> {
    
    
    private SystemConfig systemConfig;
    private GradientConfig gradientConfig;

    public PAGInit(SystemConfig systemConfig, GradientConfig gradientConfig){
        
        this.systemConfig = systemConfig;
        this.gradientConfig = gradientConfig;
    }


    public SystemConfig getSystemConfig() {
        return systemConfig;
    }

    public GradientConfig getGradientConfig() {
        return gradientConfig;
    }
}
