import torch
import torch.nn as nn
import torch.nn.init as init
import torch.nn.functional as F
from torch.utils.data import DataLoader
import math
import os
import sys
from dataset import MyDataset
import numpy as np
import time
from model import LipNet
import torch.optim as optim
import re
import json
import tempfile
import shutil
import cv2
import face_alignment



def get_position(size, padding=0.25):
    
    x = [0.000213256, 0.0752622, 0.18113, 0.29077, 0.393397, 0.586856, 0.689483, 0.799124,
                    0.904991, 0.98004, 0.490127, 0.490127, 0.490127, 0.490127, 0.36688, 0.426036,
                    0.490127, 0.554217, 0.613373, 0.121737, 0.187122, 0.265825, 0.334606, 0.260918,
                    0.182743, 0.645647, 0.714428, 0.793132, 0.858516, 0.79751, 0.719335, 0.254149,
                    0.340985, 0.428858, 0.490127, 0.551395, 0.639268, 0.726104, 0.642159, 0.556721,
                    0.490127, 0.423532, 0.338094, 0.290379, 0.428096, 0.490127, 0.552157, 0.689874,
                    0.553364, 0.490127, 0.42689]
    
    y = [0.106454, 0.038915, 0.0187482, 0.0344891, 0.0773906, 0.0773906, 0.0344891,
                    0.0187482, 0.038915, 0.106454, 0.203352, 0.307009, 0.409805, 0.515625, 0.587326,
                    0.609345, 0.628106, 0.609345, 0.587326, 0.216423, 0.178758, 0.179852, 0.231733,
                    0.245099, 0.244077, 0.231733, 0.179852, 0.178758, 0.216423, 0.244077, 0.245099,
                    0.780233, 0.745405, 0.727388, 0.742578, 0.727388, 0.745405, 0.780233, 0.864805,
                    0.902192, 0.909281, 0.902192, 0.864805, 0.784792, 0.778746, 0.785343, 0.778746,
                    0.784792, 0.824182, 0.831803, 0.824182]
    
    x, y = np.array(x), np.array(y)
    
    x = (x + padding) / (2 * padding + 1)
    y = (y + padding) / (2 * padding + 1)
    x = x * size
    y = y * size
    return np.array(list(zip(x, y)))

def cal_area(anno):
    return (anno[:,0].max() - anno[:,0].min()) * (anno[:,1].max() - anno[:,1].min()) 

def output_video(p, txt, dst):
    files = os.listdir(p)
    files = sorted(files, key=lambda x: int(os.path.splitext(x)[0]))

    font = cv2.FONT_HERSHEY_SIMPLEX
    
    for file, line in zip(files, txt):
        img = cv2.imread(os.path.join(p, file))
        h, w, _ = img.shape
        img = cv2.putText(img, line, (w//8, 11*h//12), font, 1.2, (0, 0, 0), 3, cv2.LINE_AA)
        img = cv2.putText(img, line, (w//8, 11*h//12), font, 1.2, (255, 255, 255), 0, cv2.LINE_AA)  
        h = h // 2
        w = w // 2
        img = cv2.resize(img, (w, h))     
        cv2.imwrite(os.path.join(p, file), img)
    
    cmd = f'ffmpeg -y -i "{p}/%d.jpg" -r 25 "{dst}"'
    print(f"Executing: {cmd}") # 디버깅용 로그 출력
    os.system(cmd)

def transformation_from_points(points1, points2):
    points1 = points1.astype(np.float64)
    points2 = points2.astype(np.float64)
 
    c1 = np.mean(points1, axis=0)
    c2 = np.mean(points2, axis=0)
    points1 -= c1
    points2 -= c2
    s1 = np.std(points1)
    s2 = np.std(points2)
    points1 /= s1
    points2 /= s2
 
    U, S, Vt = np.linalg.svd(points1.T * points2)
    R = (U * Vt).T
    return np.vstack([np.hstack(((s2 / s1) * R,
                                       c2.T - (s2 / s1) * R * c1.T)),
                         np.matrix([0., 0., 1.])])

def extract_frames(file):
    """영상에서 프레임 추출"""
    p = tempfile.mkdtemp()
    cmd = f'ffmpeg -y -i "{file}" -r 25 -vf "scale=-1:720" "{p}/%d.jpg"'
    os.system(cmd)
    files = sorted(os.listdir(p), key=lambda x: int(os.path.splitext(x)[0]))
    array = [cv2.imread(os.path.join(p, f)) for f in files]
    return list(filter(lambda im: im is not None, array)), p


def align_lip_region(scene, fa, front256):
    """단일 프레임에서 입술 영역 추출"""
    preds = fa.get_landmarks(scene)
    if preds is None:
        return None
    shape = np.array(preds[0])[17:]
    M = transformation_from_points(np.matrix(shape), np.matrix(front256))
    img = cv2.warpAffine(scene, M[:2], (256, 256))
    (x, y) = front256[-20:].mean(0).astype(np.int32)
    w = 160 // 2
    img = img[y-w//2:y+w//2, x-w:x+w, ...]
    return cv2.resize(img, (128, 64))


def load_video(file):
    """영상 로드 및 전처리"""
    array, p = extract_frames(file)
    fa = face_alignment.FaceAlignment(face_alignment.LandmarksType.TWO_D, flip_input=False, device='cuda')
    front256 = get_position(256)

    print(f"인식 시작: 총 {len(array)} 프레임 분석 중...")
    video = []
    for i, scene in enumerate(array):
        img = align_lip_region(scene, fa, front256)
        if img is not None:
            video.append(img)
        else:
            print(f"Frame {i}: 얼굴 인식 실패")

    if len(video) == 0:
        shutil.rmtree(p)
        raise Exception("영상 전 구간에서 얼굴을 찾을 수 없습니다.")

    video = np.stack(video, axis=0).astype(np.float32)
    return torch.FloatTensor(video.transpose(3, 0, 1, 2)) / 255.0, p

def ctc_decode(y):
    y = y.argmax(-1)
    t = y.size(0)
    result = []
    for i in range(t+1):
        result.append(MyDataset.ctc_arr2txt(y[:i], start=1))
    return result
        

if(__name__ == '__main__'):
    opt = __import__('options')
    os.environ['CUDA_VISIBLE_DEVICES'] = opt.gpu    
    
    
    model = LipNet()
    model = model.cuda()
    net = nn.DataParallel(model).cuda()

    if(hasattr(opt, 'weights')):
        pretrained_dict = torch.load(opt.weights)
        model_dict = model.state_dict()
        pretrained_dict = {k: v for k, v in pretrained_dict.items() if k in model_dict.keys() and v.size() == model_dict[k].size()}
        missed_params = [k for k, v in model_dict.items() if not k in pretrained_dict.keys()]
        print('loaded params/tot params:{}/{}'.format(len(pretrained_dict),len(model_dict)))
        print('miss matched params:{}'.format(missed_params))
        model_dict.update(pretrained_dict)
        model.load_state_dict(model_dict)
        
    video, img_p = load_video(sys.argv[1])
    y = model(video[None,...].cuda())
    txt = ctc_decode(y[0])
    
    output_video(img_p, txt, sys.argv[2])
    
    shutil.rmtree(img_p)
    
    
    