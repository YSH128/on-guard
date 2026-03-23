"""
eval pretained model.
"""
import numpy as np
import random
import yaml
from tqdm import tqdm
from trainer.metrics.utils import get_test_metrics
import torch
import torch.nn.parallel
import torch.backends.cudnn as cudnn
import torch.utils.data

from dataset.abstract_dataset import DeepfakeAbstractBaseDataset
from model.ds import DS

import argparse

parser = argparse.ArgumentParser(description='Process some paths.')
parser.add_argument('--detector_path', type=str, 
                    default='config/valid_data_test.yaml',
                    help='path to detector YAML file')
parser.add_argument("--test_dataset", nargs="+")
parser.add_argument('--weights_path', type=str,
                    #  ds_ _2024-12-20-21-48-57ds_ _2024-12-30-18-07-52
                    #ds_ _2024-12-22-15-55-57 FFIW 83 71  WDF auc: 0.8351 video_auc: 0.8747 DF10  video_auc: 0.98225  auc: 0.961988821
                    #ds_ _2024-12-26-16-47-41  WDF 85 86   DF10 0.95505  video_auc: 0.97841
                    default='weights/ckpt_best.pth')  #
#parser.add_argument("--lmdb", action='store_true', default=False)
args = parser.parse_args()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def init_seed(config):
    if config['manualSeed'] is None:
        config['manualSeed'] = random.randint(1, 10000)
    random.seed(config['manualSeed'])
    torch.manual_seed(config['manualSeed'])
    if config['cuda']:
        torch.cuda.manual_seed_all(config['manualSeed'])


def prepare_testing_data(config):
    def get_test_data_loader(config, test_name):
        # update the config dictionary with the specific testing dataset
        config = config.copy()  # create a copy of config to avoid altering the original one
        config['test_dataset'] = test_name  # specify the current test dataset
        test_set = DeepfakeAbstractBaseDataset(
                config=config,
                mode='test', 
            )
        test_data_loader = \
            torch.utils.data.DataLoader(
                dataset=test_set, 
                batch_size=config['test_batchSize'],
                shuffle=False, 
                num_workers=int(config['workers']),
                collate_fn=test_set.collate_fn,
                drop_last=False
            )
        return test_data_loader

    test_data_loaders = {}
    for one_test_name in config['test_dataset']:
        test_data_loaders[one_test_name] = get_test_data_loader(config, one_test_name)
    return test_data_loaders


def choose_metric(config):
    metric_scoring = config['metric_scoring']
    if metric_scoring not in ['eer', 'auc', 'acc', 'ap']:
        raise NotImplementedError('metric {} is not implemented'.format(metric_scoring))
    return metric_scoring


def test_one_dataset(model, data_loader):
    prediction_lists = []
    #feature_lists = []
    label_lists = []
    for i, data_dict in tqdm(enumerate(data_loader), total=len(data_loader)):
        # get data
        data, label, mask, landmark = \
        data_dict['image'], data_dict['label'], data_dict['mask'], data_dict['landmark']
        label = torch.where(data_dict['label'] != 0, 1, 0)
        # move data to GPU
        data_dict['image'], data_dict['label'] = data.to(device), label.to(device)
        if mask is not None:
            data_dict['mask'] = mask.to(device)
        if landmark is not None:
            data_dict['landmark'] = landmark.to(device)

        # model forward without considering gradient computation
        predictions = inference(model, data_dict)
        label_lists += list(data_dict['label'].cpu().detach().numpy())
        prediction_lists += list(predictions['prob'].cpu().detach().numpy())
        #feature_lists += list(predictions['feat'].cpu().detach().numpy())
    
    return np.array(prediction_lists), np.array(label_lists)#,np.array(feature_lists)
    
def test_epoch(model, test_data_loaders):
    model.eval()
    metrics_all_datasets = {}
    keys = test_data_loaders.keys()

    for key in keys:
        data_dict = test_data_loaders[key].dataset.data_dict
        predictions_nps, label_nps = test_one_dataset(model, test_data_loaders[key])
        
        import os
        img_names = data_dict['image']
        
        video_stats = {}
        for img_name, y_pred, y_true in zip(img_names, predictions_nps, label_nps):
            file_name = os.path.basename(img_name)
            
            if "_frame_" in file_name:
                vid_name = file_name.split("_frame_")[0] + ".mp4"
            else:
                vid_name = file_name
                
            if vid_name not in video_stats:
                video_stats[vid_name] = {'preds': [], 'label': y_true}
            video_stats[vid_name]['preds'].append(y_pred)
            
        tp = fp = tn = fn = 0
        
        output_lines = []
        output_lines.append("\n=== Inference Results ===")
        for vid_name, stats in video_stats.items():
            avg_prob = sum(stats['preds']) / len(stats['preds'])
            y_true = stats['label']
            
            status = "FAKE" if avg_prob > 0.5 else "REAL"
            true_status = "FAKE" if y_true == 1 else "REAL"
            result_str = "O" if status == true_status else "X"
            
            if true_status == "FAKE" and status == "FAKE":
                tp += 1
            elif true_status == "REAL" and status == "FAKE":
                fp += 1
            elif true_status == "REAL" and status == "REAL":
                tn += 1
            elif true_status == "FAKE" and status == "REAL":
                fn += 1
                
            output_lines.append(f"File: {vid_name:<30}\t| Label: {true_status}\t| Pred: {status}\t| Prob: {avg_prob:.4f}\t| 정답여부: {result_str}")
            
        output_lines.append("-----------------------------------------------------------------------------------------------------")
        output_lines.append(f"가짜(Fake) 정답(TP):\t{tp}개\t| 가짜를 진짜로 오답(FN):\t{fn}개")
        output_lines.append(f"진짜(Real) 정답(TN):\t{tn}개\t| 진짜를 가짜로 오답(FP):\t{fp}개")
        
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1_score = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
        
        output_lines.append(f"Precision (정밀도):\t{precision:.4f}")
        output_lines.append(f"Recall (재현율)   :\t{recall:.4f}")
        output_lines.append(f"F1 Score        :\t{f1_score:.4f}")
        output_lines.append("=====================================================================================================")
        
        output_text = "\n".join(output_lines)
        print(output_text)
        
        result_file = r"c:\Users\SSAFY\Desktop\juhi\S14P21A404\mydocs\result.txt"
        os.makedirs(os.path.dirname(result_file), exist_ok=True)
        with open(result_file, "w", encoding="utf-8") as f:
            f.write(output_text + "\n")

    return metrics_all_datasets

@torch.no_grad()
def inference(model, data_dict):
    predictions = model(data_dict, inference=True)
    return predictions


def main():
    # parse options and load config
    with open(args.detector_path, 'r') as f:
        config = yaml.safe_load(f)

    weights_path = None
    # If arguments are provided, they will overwrite the yaml settings
    if args.test_dataset:
        config['test_dataset'] = args.test_dataset
    if args.weights_path:
        config['weights_path'] = args.weights_path
        weights_path = args.weights_path
    
    # init seed
    init_seed(config)

    # set cudnn benchmark if needed
    if config['cudnn']:
        cudnn.benchmark = True

    # prepare the testing data loader
    test_data_loaders = prepare_testing_data(config)

    # prepare the model (detector)

    model = DS(clip_name=config['clip_model_name'],
               adapter_vit_name=config['vit_name'],
               num_quires=config['num_quires'],
               fusion_map=config['fusion_map'],
               mlp_dim=config['mlp_dim'],
               mlp_out_dim=config['mlp_out_dim'],
               head_num=config['head_num'],
               device=config['device'])
    epoch = 0
    #weights_paths = [
    #                 '/data/cuixinjie/DsClip_L_V2/logs/ds_ _2024-09-25-14-26-04/test/avg/ckpt_best.pth',
    #                ]

    try:
        epoch = int(weights_path.split('/')[-1].split('.')[0].split('_')[2])
    except:
        epoch = 0
        ckpt = torch.load(weights_path, map_location=device)
        model.load_state_dict(ckpt, strict=False)
        model.to(device)

        print(f'===> Load {weights_path} done!')

        # start testing
        best_metric = test_epoch(model, test_data_loaders)
        print('===> Test Done!')

if __name__ == '__main__':
    main()
